# Identity Service foundation

The service uses OAuth2 Authorization Code + PKCE and OpenID Connect. Access tokens have a 15-minute lifetime; refresh tokens have a 30-day lifetime and `reuseRefreshTokens(false)` enables rotation.

`sub` is the immutable `platform_users.id` UUID. Resource services use it for ownership checks. `roles` is a custom JWT claim; OAuth scopes remain in the standard `scope` claim, including `listings:write`.

## Account lifecycle

- `POST /v1/users/register` can only create buyer accounts; callers cannot submit a role.
- `POST /v1/admin/users/agents` requires `ROLE_AGENCY_ADMIN` and can provision an agent.
- Passwords require at least 12 characters and at most 72 UTF-8 bytes with uppercase, lowercase, digit, and symbol, then are stored only as BCrypt hashes.

## Security boundaries

- `OAUTH_WEB_CLIENT_SECRET` is mandatory at runtime and must come from a secret manager or environment injection.
- The `dev` profile creates an ephemeral RSA signing key. Every non-`dev` deployment must inject a PKCS#12 keystore through `app.signing.keystore-location`, with its password, alias, key password, and a stable key ID supplied from secret management.
- The keystore is a deployment-safe baseline; an AWS KMS signing adapter may replace it later without changing resource-service JWT validation.
- OAuth clients, authorization codes, refresh tokens, and OIDC consent are persisted through Spring Authorization Server JDBC repositories. This supports refresh-token rotation across restart and multiple service replicas.

## Local development

The workspace Compose setup supplies `DB_PASSWORD`, `OAUTH_WEB_CLIENT_SECRET`, and the `dev` profile. Signing-key configuration is validated only outside `dev`; a development restart generates a new signing key and invalidates old signed tokens.

`DEV_ADMIN_BOOTSTRAP_ENABLED=true` enables first-admin creation only in the `dev` profile, using `DEV_ADMIN_EMAIL` and `DEV_ADMIN_PASSWORD`. Repeated starts retain the existing administrator and password. An existing buyer or agent with that email causes startup to fail instead of being promoted. Production needs a separately controlled administrator provisioning procedure.

Account APIs under `/v1/**` are stateless and use bearer authentication; they ignore interactive login sessions. Public JSON registration does not need CSRF. `/login`, `/logout`, and browser OAuth sessions retain CSRF protection. Unauthenticated HTML authorization requests redirect to `/login`.

Custom `roles` claims use `ArrayList` so persisted OAuth state can be safely read by Spring Security's JDBC deserialization allowlist; Java's immutable `Stream.toList()` representation failed in the actual OIDC/refresh flow.

The web OAuth client is created once. Changing the environment secret or redirect URI does not replace an existing database client; credential rotation and client changes need an explicit administration procedure.

`/login` renders Mongolian labels and a server-generated CSRF token; credential processing remains in Spring Security. Web post-logout redirect URIs use `/login` on already trusted callback origins. Startup adds missing logout URIs to existing clients without rotating their secrets or callback settings. The implemented Next.js callback validates state, nonce, issuer, audience, and signatures before creating an encrypted HttpOnly web session.

## Verification and references

`mvn verify -Pintegration` validates registration, role boundaries, login CSRF, login redirects, actual PKCE, UserInfo, JDBC reload, refresh rotation, and code/token reuse rejection using a disposable PostgreSQL database.

- [Spring Authorization Server configuration](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
