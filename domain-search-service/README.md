# GerHub Search

Python 3.13, FastAPI, PostgreSQL inbox/projections/jobs, OpenSearch 3.9, Redis shared rate buckets.

Start as part of `bash scripts/local.sh up`. The loopback host API defaults to `http://localhost:8001` in this workspace because 8000 is occupied; Compose uses `search:8000` internally. All Python execution happens inside an activated Conda environment named `rl_env_gpu_clean`:

```bash
docker compose --env-file .local.env -f domain-platform-infra/compose.local.yaml run --rm --no-deps search \
  bash -c 'source rl_env_gpu_clean/bin/activate && python -m pytest -q -p no:cacheprovider tests'
```

- `GET /v1/search`: q, suburb, propertyType, listingType, currency, minPrice, maxPrice, minBedrooms, latitude/longitude/radiusKm (all three required), cursor. Active listings only. Facets and stable createdAt/UUID `search_after`; the opaque cursor binds to its criteria. Radius <=200 km; 20 results per page.
- `POST /internal/events`: constant-time `X-Internal-Key`, full `property.updated.v1` snapshots. Inbox IDs deduplicate. Older aggregate versions are ignored; changed snapshots at an equal version conflict. PostgreSQL jobs retry index writes using `external_gte` with version+1. Private snapshots remain as tombstones; public queries hard-filter ACTIVE. Web/native gateway checks current listing status before displaying projections.
- `/v1/users/me/searches`: authenticated GET/POST, DELETE `/{id}`. Max50, per-account write lock; body `{name, criteria, emailEnabled}`.
- `/v1/users/me/alerts`: personal GET, POST `/{id}/read`. One alert per saved-search/property, produced only for later matching updates. Optional email uses durable delivery to Identity with an idempotent notification ID.
- Access-token scope, JWT signature, issuer, expiry, web/mobile audience, UUID subject and Identity auth epoch all verified; ID tokens are rejected. Internal routes fail closed without their key; body64KiB and Redis write/read rate bounds apply.

The index is eventually consistent; it can lag committed listing changes during outages. PostgreSQL remains the durable projection/inbox/job source. Redis is bounded to64MiB; index availability is required for discovery. Saved-search email delivery stops after12 failures for support review; this is observable in `email_jobs`, not silently marked successful.

Reference: [OpenSearch document versioning](https://docs.opensearch.org/latest/api-reference/document-apis/index-document/).
