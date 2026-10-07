# Backend foundation

`ultimate_backend_mastery_guide.md` is the implementation checklist for this service.

## Phase 1 baseline

- **Module 1:** Versioned REST, HTTP semantics, cursor pagination, consistent error responses, correlation IDs.
- **Module 2:** JWT resource-server validation, role and ownership checks, least privilege, no secrets in source control.
- **Module 3:** PostgreSQL migrations, explicit transaction boundaries, indexes justified by query patterns, optimistic locking.
- **Module 5:** Hexagonal boundaries: web adapter → application use case → domain → persistence/event adapters; unit and integration tests.
- **Module 6:** Docker-ready configuration, health endpoints, structured logs and metrics.

## Implemented first vertical slice

- `POST /v1/properties`, `GET /v1/properties/{propertyId}`, `PUT /v1/properties/{propertyId}`, and `PATCH /v1/properties/{propertyId}/status`.
- Mutations require an OAuth2 JWT scope of `listings:write`; its `sub` must be the UUID of the listing's owning agent.
- `If-Match` ETags protect replacements and status changes from lost updates.
- Create requests store a 24-hour idempotency record: the same key, agent, and payload replay the original result; changed reuse returns `409`.
- Public browse uses a keyset cursor over `(created_at DESC, id DESC)`, with partial PostgreSQL indexes for active listings. A later Search Service will own full-text, geo, and faceted search.
- Media upload uses a private S3 bucket and 10-minute presigned PUT URL. The server-generated key does not include a user-controlled filename; only JPEG, PNG, and WebP images up to 25 MB are accepted in this first slice.
- Listing mutations validate the Identity issuer, require `ROLE_AGENT`, and require the `listings:write` OAuth scope. Buyers cannot create or modify listings even if a client requests that scope.
- Draft and withdrawn detail responses require the owning agent's subject; public requests receive `404`.
- Concurrent create retries acquire a transaction-scoped PostgreSQL advisory lock before reading or writing their idempotency record. Response snapshots retain the original body and ETag after later edits; expired keys can be reused without deleting listings.
- Pre-upgrade idempotency records without a snapshot still recognize the previous fingerprint and return the current listing state, matching their former behavior. Original historical response bodies cannot be recovered for those records.
- Every committed create, replacement, and status transition writes a `property.updated.v1` projection into `outbox_events` in the same transaction. Publishing to Kafka is deferred; events remain unpublished until a delivery worker is implemented.
- Object storage is opt-in with `MEDIA_ENABLED=true`. The default local configuration returns a Problem Details `503` and rolls back pending media metadata.

## Test commands

- `mvn test` runs fast unit tests without Docker.
- `mvn verify -Pintegration` runs PostgreSQL/Flyway integration tests through Testcontainers and requires a reachable Docker daemon.

## Deferred deliberately

Kafka delivery, OpenSearch indexing, rate limiting, Kubernetes, and payment workflows are introduced after the transactional Listing API is tested locally. Their contracts are established now so later extraction does not break clients.

The image API currently creates pending uploads only. Confirmation through S3 HEAD/content verification, `UPLOADED` transitions, signed download URLs, duplicate display-order handling, and stale-upload cleanup remain unfinished. Do not treat presigning alone as an implemented media lifecycle.
