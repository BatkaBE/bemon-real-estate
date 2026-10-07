"""Versioned property projections, OpenSearch discovery, and principal-scoped alerts."""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import os
import threading
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator
from uuid import UUID, uuid4

import httpx
import jwt
import psycopg
import redis
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from pydantic import BaseModel, Field, model_validator

LOGGER = logging.getLogger("bemon.search")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
DATABASE_URL = os.environ.get("DATABASE_URL", "postgresql://localhost/search_db")
OPENSEARCH_URL = os.environ.get("OPENSEARCH_URL", "http://localhost:9200")
IDENTITY_URL = os.environ.get("IDENTITY_INTERNAL_URL", "http://localhost:9000")
ISSUER = os.environ.get("IDENTITY_ISSUER_URI", "http://localhost:9000")
INTERNAL_KEY = os.environ.get("INTERNAL_SERVICE_KEY", "")
INDEX = "bemon-properties-v1"
HTTP = httpx.Client(timeout=5.0)
CACHE = redis.Redis.from_url(os.environ.get("REDIS_URL", "redis://localhost:6379"), socket_timeout=1)
KEYS = jwt.PyJWKClient(os.environ.get("IDENTITY_JWK_SET_URI", f"{IDENTITY_URL}/oauth2/jwks"), timeout=3)
PAGE_SIZE = 20
MAX_BODY_BYTES = 65536


class Criteria(BaseModel):
    """Bounds public query complexity and geographic coordinates before index access."""
    q: str = Field(default="", max_length=120)
    suburb: str = Field(default="", max_length=100)
    currency: str = Field(default="MNT", pattern="^(MNT|AUD)$")
    propertyType: str = Field(default="", pattern="^(|HOUSE|APARTMENT|TOWNHOUSE|LAND|UNIT)$")
    listingType: str = Field(default="", pattern="^(|SALE|RENT)$")
    minPrice: float | None = Field(default=None, ge=0, le=9999999999)
    maxPrice: float | None = Field(default=None, ge=0, le=9999999999)
    minBedrooms: int | None = Field(default=None, ge=0, le=100)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    radiusKm: float | None = Field(default=None, gt=0, le=200)

    @model_validator(mode="after")
    def coherent(self) -> "Criteria":
        """Rejects partial coordinates and inverted price intervals."""
        geo = [self.latitude, self.longitude, self.radiusKm]
        if any(value is not None for value in geo) and not all(value is not None for value in geo):
            raise ValueError("Latitude, longitude and radius are required together")
        if self.minPrice is not None and self.maxPrice is not None and self.minPrice > self.maxPrice:
            raise ValueError("Price interval is inverted")
        return self


class SavedSearch(BaseModel):
    """Client input without a selectable owner."""
    name: str = Field(min_length=1, max_length=120)
    criteria: Criteria
    emailEnabled: bool = False


class PropertyEvent(BaseModel):
    """Full event envelope accepted only over the authenticated internal boundary."""
    eventId: UUID
    eventType: str = Field(pattern="^property.updated.v1$")
    aggregateId: UUID
    aggregateVersion: int = Field(ge=0)
    occurredAt: datetime
    data: dict[str, Any]


def connection() -> psycopg.Connection:
    """Opens a transaction-owned PostgreSQL connection with mapping rows."""
    return psycopg.connect(DATABASE_URL, row_factory=dict_row, connect_timeout=3)


def internal(key: str = Header(default="", alias="X-Internal-Key")) -> None:
    """Fails closed when either internal credential is absent or mismatched."""
    if not INTERNAL_KEY or not hmac.compare_digest(key, INTERNAL_KEY):
        raise HTTPException(403, "Service credential required")


def principal(authorization: str = Header(default="")) -> str:
    """Verifies access-token scope, signature, issuer, audience, expiry and revocation."""
    try:
        if not authorization.startswith("Bearer "):
            raise ValueError("Missing bearer")
        token = authorization[7:]
        claims = jwt.decode(token, KEYS.get_signing_key_from_jwt(token).key, algorithms=["RS256"],
                            issuer=ISSUER, audience=["domain-web", "domain-mobile"],
                            options={"require": ["exp", "sub", "iss", "aud", "scope"]})
        user_id = str(UUID(claims["sub"]))
        response = HTTP.get(f"{IDENTITY_URL}/internal/accounts/{user_id}/version",
                            headers={"X-Internal-Key": INTERNAL_KEY})
        response.raise_for_status()
        if response.json()["version"] != int(claims.get("auth_version", "0")):
            raise ValueError("Revoked")
        return user_id
    except Exception as error:
        raise HTTPException(401, "Invalid credentials", headers={"WWW-Authenticate": "Bearer"}) from error


def query_for(criteria: Criteria) -> dict[str, Any]:
    """Builds index queries with hard publication and currency filters."""
    filters: list[dict[str, Any]] = [{"term": {"status": "ACTIVE"}}, {"term": {"currency": criteria.currency}}]
    for key in ("suburb", "propertyType", "listingType"):
        value = getattr(criteria, key)
        if value:
            filters.append({"term": {key: value}})
    price = {key: value for key, value in (("gte", criteria.minPrice), ("lte", criteria.maxPrice)) if value is not None}
    if price:
        filters.append({"range": {"price": price}})
    if criteria.minBedrooms is not None:
        filters.append({"range": {"bedrooms": {"gte": criteria.minBedrooms}}})
    if criteria.radiusKm is not None:
        filters.append({"geo_distance": {"distance": f"{criteria.radiusKm}km",
                                         "location": {"lat": criteria.latitude, "lon": criteria.longitude}}})
    result: dict[str, Any] = {"bool": {"filter": filters}}
    if criteria.q.strip():
        result["bool"]["must"] = [{"multi_match": {"query": criteria.q.strip(), "fields": ["title^3", "addressText"], "operator": "and"}}]
    return result


def initialize_index() -> None:
    """Creates explicit mappings; an existing index is preserved."""
    mappings = {"properties": {"id": {"type": "keyword"}, "createdAt": {"type": "date"},
                              "location": {"type": "geo_point"}, "title": {"type": "text"},
                              "addressText": {"type": "text"}, "price": {"type": "double"},
                              "bedrooms": {"type": "integer"}}}
    for name in ("status", "currency", "suburb", "propertyType", "listingType"):
        mappings["properties"][name] = {"type": "keyword"}
    response = HTTP.put(f"{OPENSEARCH_URL}/{INDEX}", json={"settings": {"number_of_shards": 1, "number_of_replicas": 0}, "mappings": mappings})
    if response.status_code != 400 or response.json().get("error", {}).get("type") != "resource_already_exists_exception":
        response.raise_for_status()


def index_document(data: dict[str, Any]) -> dict[str, Any]:
    """Adds index-only fields while retaining the complete public response shape."""
    result = dict(data)
    result["suburb"] = data["address"]["suburb"]
    result["addressText"] = " ".join(str(value) for key, value in data["address"].items() if key not in ("latitude", "longitude"))
    result["location"] = {"lat": data["address"]["latitude"], "lon": data["address"]["longitude"]}
    return result


def ingest(event: PropertyEvent) -> None:
    """Deduplicates events and atomically queues only newer aggregate snapshots."""
    payload = event.model_dump(mode="json")
    data = payload["data"]
    if str(data.get("id")) != str(event.aggregateId) or data.get("version") != event.aggregateVersion:
        raise HTTPException(400, "Inconsistent aggregate snapshot")
    index_document(data)  # Validate required address fields before accepting delivery.
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    with connection() as db:
        db.execute("INSERT INTO inbox(event_id,fingerprint) VALUES(%s,%s) ON CONFLICT DO NOTHING", (event.eventId, digest))
        if db.execute("SELECT fingerprint FROM inbox WHERE event_id=%s", (event.eventId,)).fetchone()["fingerprint"] != digest:
            raise HTTPException(409, "Event identity conflict")
        row = db.execute("INSERT INTO projections(id,version,document) VALUES(%s,%s,%s) ON CONFLICT(id) DO UPDATE SET version=excluded.version,document=excluded.document WHERE projections.version<excluded.version RETURNING id",
                         (event.aggregateId, event.aggregateVersion, Jsonb(data))).fetchone()
        if not row:
            current = db.execute("SELECT version,document FROM projections WHERE id=%s", (event.aggregateId,)).fetchone()
            if current["version"] == event.aggregateVersion and current["document"] != data:
                raise HTTPException(409, "Aggregate version conflict")
        if row:
            db.execute("INSERT INTO index_jobs(id,version) VALUES(%s,%s) ON CONFLICT(id) DO UPDATE SET version=excluded.version,attempts=0,next_attempt=now()", (event.aggregateId, event.aggregateVersion))


def deliver_index() -> None:
    """Indexes monotonic versions; private tombstones stay indexed to prevent resurrection."""
    with connection() as db:
        jobs = db.execute("SELECT j.*,p.document FROM index_jobs j JOIN projections p ON p.id=j.id WHERE j.next_attempt<=now() ORDER BY j.next_attempt LIMIT 5 FOR UPDATE OF j SKIP LOCKED").fetchall()
        for job in jobs:
            try:
                data = index_document(job["document"])
                response = HTTP.put(f"{OPENSEARCH_URL}/{INDEX}/_doc/{job['id']}", params={"version": job["version"] + 1, "version_type": "external_gte", "refresh": "wait_for"}, json=data)
                response.raise_for_status()
                saved = db.execute("SELECT * FROM saved_searches WHERE created_at<=%s", (data["updatedAt"],)).fetchall()
                for search in saved:
                    criteria = Criteria.model_validate(search["criteria"])
                    query = query_for(criteria)
                    query["bool"]["filter"].append({"ids": {"values": [str(job["id"])]}})
                    match = HTTP.post(f"{OPENSEARCH_URL}/{INDEX}/_search", json={"query": query, "size": 0}).json()
                    if match.get("hits", {}).get("total", {}).get("value", 0):
                        alert_id = uuid4()
                        inserted = db.execute("INSERT INTO alerts(id,search_id,property_id,user_id,title) VALUES(%s,%s,%s,%s,%s) ON CONFLICT(search_id,property_id) DO NOTHING RETURNING id",
                                              (alert_id, search["id"], job["id"], search["user_id"], data["title"])).fetchone()
                        if inserted and search["email_enabled"]:
                            db.execute("INSERT INTO email_jobs(id,user_id,subject,body) VALUES(%s,%s,%s,%s)", (alert_id, search["user_id"], "Bemon — хайлтад тохирох зар", "Хадгалсан хайлтад тохирох зар нэмэгдлээ. Bemon-ийн мэдэгдэл хэсгээс үзнэ үү."))
                db.execute("DELETE FROM index_jobs WHERE id=%s AND version=%s", (job["id"], job["version"]))
                try:
                    CACHE.incr("bemon:search:generation")
                except redis.RedisError:
                    pass
            except Exception:
                db.execute("UPDATE index_jobs SET attempts=attempts+1,next_attempt=now()+(LEAST(300,power(2,LEAST(attempts,8))) * interval '1 second') WHERE id=%s", (job["id"],))
                LOGGER.warning("Index delivery deferred; event_id=%s", job["id"])


def deliver_email() -> None:
    """Acknowledges notification delivery only after the Identity outbox accepts it."""
    with connection() as db:
        for job in db.execute("SELECT * FROM email_jobs WHERE next_attempt<=now() AND attempts<12 LIMIT 5 FOR UPDATE SKIP LOCKED").fetchall():
            try:
                response = HTTP.post(f"{IDENTITY_URL}/internal/notifications", headers={"X-Internal-Key": INTERNAL_KEY}, json={"notificationId": str(job["id"]), "userId": str(job["user_id"]), "subject": job["subject"], "body": job["body"]})
                response.raise_for_status()
                db.execute("DELETE FROM email_jobs WHERE id=%s", (job["id"],))
            except Exception:
                db.execute("UPDATE email_jobs SET attempts=attempts+1,next_attempt=now()+interval '1 minute' WHERE id=%s", (job["id"],))


def worker(stop: threading.Event) -> None:
    """Retries bounded jobs until the process receives shutdown."""
    ready = False
    while not stop.wait(1):
        try:
            if not ready:
                initialize_index()
                ready = True
            deliver_index()
            deliver_email()
        except Exception:
            LOGGER.warning("Search worker dependency unavailable")


@asynccontextmanager
async def lifespan(_: FastAPI) -> Iterator[None]:
    """Initializes durable tables and joins the background worker on shutdown."""
    with connection() as db:
        db.execute("SELECT pg_advisory_xact_lock(hashtext('bemon-search-schema-v1'))")
        db.execute(Path(__file__).with_name("schema.sql").read_text())
    stop = threading.Event()
    thread = threading.Thread(target=worker, args=(stop,), daemon=True)
    thread.start()
    yield
    stop.set()
    thread.join(timeout=6)


app = FastAPI(title="Bemon Search", lifespan=lifespan, docs_url=None, redoc_url=None)


@app.middleware("http")
async def bounds(request: Request, call_next: Any) -> Any:
    """Bounds chunked bodies and shared request rate without trusting proxy headers."""
    body = b""
    async for chunk in request.stream():
        body += chunk
        if len(body) > MAX_BODY_BYTES:
            return JSONResponse({"detail": "Request too large"}, status_code=413)
    request._body = body
    if not request.url.path.startswith("/internal"):
        try:
            bucket = f"bemon:rate:{request.client.host}:{int(time.time() // 60)}"
            count = CACHE.incr(bucket)
            if count == 1:
                CACHE.expire(bucket, 70)
            if count > 240:
                return JSONResponse({"detail": "Too many requests"}, status_code=429, headers={"Retry-After": "60"})
        except redis.RedisError:
            pass
    try:
        return await call_next(request)
    except ValueError:
        return JSONResponse({"detail": "Invalid request criteria"}, status_code=422)


@app.get("/health")
def health() -> dict[str, str]:
    """Confirms durable search storage and index connectivity."""
    try:
        with connection() as db:
            db.execute("SELECT 1")
        HTTP.get(f"{OPENSEARCH_URL}/_cluster/health").raise_for_status()
        return {"status": "UP"}
    except Exception as error:
        raise HTTPException(503, "Search unavailable") from error


@app.post("/internal/events", dependencies=[Depends(internal)])
def events(event: PropertyEvent) -> dict[str, str]:
    """Accepts authenticated delivery before asynchronous index propagation."""
    try:
        ingest(event)
    except (KeyError, TypeError, ValueError) as error:
        raise HTTPException(400, "Invalid snapshot") from error
    return {"status": "accepted"}


@app.get("/v1/search")
def search(criteria: Criteria = Depends(), cursor: str = "") -> dict[str, Any]:
    """Returns publication-filtered full-text results, facets, geo matches and keyset pagination."""
    fingerprint = hashlib.sha256(criteria.model_dump_json().encode()).hexdigest()
    query = {"query": query_for(criteria), "size": PAGE_SIZE + 1, "sort": [{"createdAt": "desc"}, {"id": "desc"}],
             "aggs": {name: {"terms": {"field": name, "size": 30}} for name in ("suburb", "propertyType", "listingType")}}
    if cursor:
        try:
            if len(cursor) > 1024:
                raise ValueError("Oversized cursor")
            decoded = json.loads(base64.urlsafe_b64decode(cursor + "=" * (-len(cursor) % 4)))
            if decoded["query"] != fingerprint or len(decoded["sort"]) != 2:
                raise ValueError("Query cursor mismatch")
            query["search_after"] = decoded["sort"]
        except Exception as error:
            raise HTTPException(400, "Invalid cursor") from error
    try:
        response = HTTP.post(f"{OPENSEARCH_URL}/{INDEX}/_search", json=query)
        response.raise_for_status()
        result = response.json()
    except Exception as error:
        raise HTTPException(503, "Search unavailable") from error
    hits = result["hits"]["hits"]
    items = [{k: v for k, v in hit["_source"].items() if k not in ("location", "addressText", "suburb")} for hit in hits[:PAGE_SIZE]]
    next_cursor = None
    if len(hits) > PAGE_SIZE:
        next_cursor = base64.urlsafe_b64encode(json.dumps({"query": fingerprint, "sort": hits[PAGE_SIZE - 1]["sort"]}).encode()).decode().rstrip("=")
    return {"items": items, "nextCursor": next_cursor, "pageSize": PAGE_SIZE, "total": result["hits"]["total"]["value"],
            "facets": {key: value["buckets"] for key, value in result.get("aggregations", {}).items()}}


@app.get("/v1/users/me/searches")
def searches(user_id: str = Depends(principal)) -> dict[str, Any]:
    """Lists only the authenticated user's bounded saved-search collection."""
    with connection() as db:
        rows = db.execute("SELECT id,name,criteria,email_enabled AS \"emailEnabled\",created_at AS \"createdAt\" FROM saved_searches WHERE user_id=%s ORDER BY created_at DESC LIMIT 50", (user_id,)).fetchall()
    return {"items": rows}


@app.post("/v1/users/me/searches", status_code=201)
def save(body: SavedSearch, user_id: str = Depends(principal)) -> dict[str, str]:
    """Serializes per-account writes so the collection limit cannot be bypassed concurrently."""
    with connection() as db:
        db.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (user_id,))
        if db.execute("SELECT count(*) AS count FROM saved_searches WHERE user_id=%s", (user_id,)).fetchone()["count"] >= 50:
            raise HTTPException(409, "Saved-search limit reached")
        search_id = uuid4()
        db.execute("INSERT INTO saved_searches(id,user_id,name,criteria,email_enabled) VALUES(%s,%s,%s,%s,%s)", (search_id, user_id, body.name, Jsonb(body.criteria.model_dump()), body.emailEnabled))
    return {"id": str(search_id)}


@app.delete("/v1/users/me/searches/{search_id}")
def remove(search_id: UUID, user_id: str = Depends(principal)) -> dict[str, str]:
    """Removes only a caller-owned saved search and its associated alerts."""
    with connection() as db:
        db.execute("DELETE FROM saved_searches WHERE id=%s AND user_id=%s", (search_id, user_id))
    return {"status": "deleted"}


@app.get("/v1/users/me/alerts")
def alerts(user_id: str = Depends(principal)) -> dict[str, Any]:
    """Returns at most fifty personal alerts with read state."""
    with connection() as db:
        rows = db.execute("SELECT id,property_id AS \"propertyId\",title,created_at AS \"createdAt\",read_at AS \"readAt\" FROM alerts WHERE user_id=%s ORDER BY created_at DESC LIMIT 50", (user_id,)).fetchall()
    return {"items": rows}


@app.post("/v1/users/me/alerts/{alert_id}/read")
def read_alert(alert_id: UUID, user_id: str = Depends(principal)) -> dict[str, str]:
    """Marks only the caller's alert as read, idempotently."""
    with connection() as db:
        db.execute("UPDATE alerts SET read_at=COALESCE(read_at,now()) WHERE id=%s AND user_id=%s", (alert_id, user_id))
    return {"status": "read"}
