# Domain Listing Service

Additional public favorites/inquiries/featured endpoints are described by the [engagement contract](../domain-platform-contracts/openapi/engagement-service-v1.yaml). Account access-token scope, web/mobile audience and epoch checks reject ID tokens and revoked credentials in the local platform.

The Mongolian web MVP uses explicit MNT currency and `/v1/agents/me/properties` for the authenticated agent's private dashboard. V5 labels retained Australian rows as AUD without converting amounts. Currency omission on replacement preserves the current label; create defaults to MNT. See [the workspace readiness report](../docs/readiness.md).

Owns property listings, property media metadata, lifecycle status, and agent authorization.

**Stack:** Java 17, Spring Boot 3.5.16, PostgreSQL, Flyway.

This is the first implementation repository. Its public APIs and events must be defined in `domain-platform-contracts` before implementation.

Use the [workspace instructions](../README.md) for the isolated local stack. `mvn test` runs fast tests and `mvn verify -Pintegration` adds PostgreSQL and HTTP tests.

Agent writes require both `ROLE_AGENT` and `listings:write`. Draft and withdrawn details are visible only to their owner. Create uses a 24-hour retained response and a transaction-scoped PostgreSQL lock for concurrent idempotent retries. Updates and status changes use strong numeric ETags; each committed mutation stores a versioned outbox projection in the same transaction.

`DB_PASSWORD` is required. `IDENTITY_ISSUER_URI` controls token issuer validation; `IDENTITY_JWK_SET_URI` independently controls key retrieval so Compose can use the internal Identity hostname. Media is disabled by default and returns `503` until `MEDIA_ENABLED=true` and AWS storage settings and credentials are supplied.

The older standalone `compose.yaml` starts only a listing database and requires `DB_PASSWORD`. Its loopback host port defaults to `55439`, overridable with `BEMON_LISTING_DB_PORT`; set the application's `DB_PORT` to the same value when running the service on the host. Prefer the workspace stack to run both services together.
