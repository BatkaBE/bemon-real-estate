# Identity Service foundation

The service uses OAuth2 Authorization Code + PKCE and OpenID Connect. Access tokens have a 15-minute lifetime; refresh tokens have a 30-day lifetime and `reuseRefreshTokens(false)` enables rotation.

`sub` is the immutable `platform_users.id` UUID. Resource services use it for ownership checks. `roles` is a custom JWT claim; OAuth scopes remain in the standard `scope` claim, including `listings:write`.

## Account lifecycle

- `POST /v1/users/register` can only create buyer accounts; callers cannot submit a role.
- `POST /v1/admin/users/agents` requires `ROLE_AGENCY_ADMIN` and can provision an agent.
- Passwords require 12–72 characters with uppercase, lowercase, digit, and symbol, then are stored only as BCrypt hashes.

## Security boundaries

- `OAUTH_WEB_CLIENT_SECRET` is mandatory at runtime and must come from a secret manager or environment injection.
- The `dev` profile creates an ephemeral RSA signing key. Every non-`dev` deployment must inject a PKCS#12 keystore through `app.signing.keystore-location`, with its password, alias, key password, and a stable key ID supplied from secret management.
- The keystore is a deployment-safe baseline; an AWS KMS signing adapter may replace it later without changing resource-service JWT validation.
- OAuth clients, authorization codes, refresh tokens, and OIDC consent are persisted through Spring Authorization Server JDBC repositories. This supports refresh-token rotation across restart and multiple service replicas.
