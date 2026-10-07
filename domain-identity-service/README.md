# GerHub Identity Service

Owns user accounts, roles, authentication, authorization, and token lifecycle.

**Stack:** Java 17, Spring Boot 3.5.16, PostgreSQL, Flyway, Spring Authorization Server.

It is an independent security boundary; no other service reads its database directly.

Run the local platform using the [workspace instructions](../README.md). Fast tests: `mvn test`; database and actual OAuth tests: `mvn verify -Pintegration`.

JSON registration always creates a buyer. Agent provisioning requires an agency administrator's bearer token. Browser login retains CSRF protection; the JSON APIs use a separate stateless bearer filter chain.

Self-service APIs expose profile/contact, single-use email verification and password recovery. Password reset revokes OAuth grants, interactive and encrypted web/native sessions, and the JWT account epoch. Durable SMTP delivery uses a private outbox; challenges are never returned by public APIs. Application APIs accept access-token scopes and explicit web/mobile audiences; ID tokens remain valid only for their OIDC purpose. Shared browser/session tables and a persistent development signing key survive container restarts.

See [identity configuration](docs.md) and the [versioned account API](../domain-platform-contracts/openapi/identity-service-v1.yaml).
