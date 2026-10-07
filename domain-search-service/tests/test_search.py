"""Contract, privacy and real PostgreSQL/OpenSearch tests in an isolated synthetic namespace."""
import copy
import json
from datetime import datetime, timezone
from uuid import uuid4

import httpx
import psycopg
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from psycopg.conninfo import make_conninfo

from app import main


@pytest.fixture(autouse=True)
def isolated(monkeypatch):
    """Allocates disposable test tables and index without mutating existing application data."""
    original = main.DATABASE_URL
    namespace = "test_" + uuid4().hex
    with psycopg.connect(original) as db:
        db.execute(f'CREATE SCHEMA "{namespace}"')
    monkeypatch.setattr(main, "DATABASE_URL", make_conninfo(original, options=f"-c search_path={namespace}"))
    monkeypatch.setattr(main, "INDEX", namespace)
    with main.connection() as db:
        db.execute(main.Path(main.__file__).with_name("schema.sql").read_text())
    main.initialize_index()
    yield
    main.app.dependency_overrides.clear()
    main.HTTP.delete(f"{main.OPENSEARCH_URL}/{namespace}").raise_for_status()
    with psycopg.connect(original) as db:
        db.execute(f'DROP SCHEMA "{namespace}" CASCADE')


def event(version=0, status="ACTIVE", property_id=None, title="Гэр бүлийн орон сууц"):
    """Creates a complete contract snapshot with a current timestamp and Mongolian coordinates."""
    property_id = str(property_id or uuid4())
    now = datetime.now(timezone.utc).isoformat()
    return main.PropertyEvent.model_validate({"eventId": str(uuid4()), "eventType": "property.updated.v1", "aggregateId": property_id, "aggregateVersion": version, "occurredAt": now,
        "data": {"id": property_id, "agentId": str(uuid4()), "title": title, "propertyType": "APARTMENT", "listingType": "SALE", "price": 200000000, "currency": "MNT", "bedrooms": 2,
                 "bathrooms": 1, "parkingSpaces": 0, "landSizeSqm": 60, "address": {"addressLine": "Туршилтын хаяг", "suburb": "Хан-Уул", "state": "UB", "postcode": "17000", "latitude": 47.9188, "longitude": 106.9177},
                 "status": status, "version": version, "createdAt": now, "updatedAt": now}})


def test_replay_and_stale_tombstone():
    """Old snapshots cannot resurrect a withdrawn property; changed event IDs conflict."""
    active = event()
    main.ingest(active)
    main.ingest(active)
    main.deliver_index()
    assert main.search(main.Criteria(q="орон"))["total"] == 1
    withdrawn = event(2, "WITHDRAWN", active.aggregateId)
    main.ingest(withdrawn)
    main.ingest(event(1, "ACTIVE", active.aggregateId))
    main.deliver_index()
    assert main.search(main.Criteria())["total"] == 0
    mutated = active.model_copy(deep=True)
    mutated.data["title"] = "Changed"
    with pytest.raises(HTTPException) as conflict:
        main.ingest(mutated)
    assert conflict.value.status_code == 409
    with main.connection() as db:
        assert db.execute("SELECT version FROM projections WHERE id=%s", (active.aggregateId,)).fetchone()["version"] == 2


def test_fulltext_facets_and_geo():
    """Actual OpenSearch provides full text, currency/price/room filters, facets and distance exclusion."""
    main.ingest(event())
    private = event(status="DRAFT")
    main.ingest(private)
    aud = event()
    aud.data["currency"] = "AUD"
    main.ingest(aud)
    main.deliver_index()
    result = main.search(main.Criteria(q="орон сууц", minBedrooms=2, maxPrice=210000000, latitude=47.9188, longitude=106.9177, radiusKm=2))
    assert len(result["items"]) == 1
    assert result["facets"]["suburb"] == [{"key": "Хан-Уул", "doc_count": 1}]
    assert main.search(main.Criteria(q="байхгүй үг"))["total"] == 0
    assert main.search(main.Criteria(latitude=0, longitude=0, radiusKm=2))["total"] == 0
    assert main.search(main.Criteria(minBedrooms=3))["total"] == 0


def test_cursor_query_binding_and_ties():
    """Tied timestamps paginate by UUID and cannot reuse a cursor for a different query."""
    timestamp = datetime.now(timezone.utc).isoformat()
    for index in range(21):
        value = event()
        value.data["createdAt"] = timestamp
        main.ingest(value)
    for _ in range(5):
        main.deliver_index()
    first = main.search(main.Criteria())
    second = main.search(main.Criteria(), first["nextCursor"])
    assert len(first["items"]) == 20 and len(second["items"]) == 1
    assert len({item["id"] for item in first["items"] + second["items"]}) == 21
    with pytest.raises(HTTPException):
        main.search(main.Criteria(suburb="Баянзүрх"), first["nextCursor"])


def test_saved_search_owner_alert_once():
    """Matching updates create one personal alert and another account cannot read or remove it."""
    owner, stranger = str(uuid4()), str(uuid4())
    saved = main.save(main.SavedSearch(name="Гэр", criteria=main.Criteria(q="орон")), owner)
    active = event()
    main.ingest(active)
    main.deliver_index()
    main.ingest(event(1, property_id=active.aggregateId))
    main.deliver_index()
    assert len(main.alerts(owner)["items"]) == 1
    assert main.alerts(stranger)["items"] == []
    main.remove(main.UUID(saved["id"]), stranger)
    assert len(main.searches(owner)["items"]) == 1
    alert_id = main.alerts(owner)["items"][0]["id"]
    main.read_alert(alert_id, stranger)
    assert main.alerts(owner)["items"][0]["readAt"] is None
    main.read_alert(alert_id, owner)
    assert main.alerts(owner)["items"][0]["readAt"] is not None


def test_http_internal_and_body_bounds(monkeypatch):
    """The HTTP boundary rejects missing service keys, absent JWTs and oversized chunked JSON."""
    monkeypatch.setattr(main, "INTERNAL_KEY", "test-secret")
    with TestClient(main.app) as client:
        assert client.post("/internal/events", json=event().model_dump(mode="json")).status_code == 403
        assert client.get("/v1/users/me/searches").status_code == 401
        assert client.post("/internal/events", content=b"x" * 65537).status_code == 413
        assert client.post("/internal/events", headers={"X-Internal-Key": "test-secret"}, json=event().model_dump(mode="json")).status_code == 200


def test_inconsistent_snapshot_and_invalid_geo():
    """Untrusted snapshots and incomplete radius queries fail validation before indexing."""
    value = event()
    value.data["version"] = 50
    with pytest.raises(HTTPException):
        main.ingest(value)
    with pytest.raises(ValueError):
        main.Criteria(latitude=47)
    with pytest.raises(ValueError):
        main.Criteria(minPrice=200, maxPrice=100)
