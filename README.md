# GerHub — Real Estate Platform

This workspace contains the GerHub real-estate platform's implemented Identity, Listing, Search and Payment services, Mongolian/MNT Next.js web client, and Expo Android/iOS application.

The product name is GerHub. Existing `bemon-local` Compose volumes, `BEMON_*` settings, session/storage keys, search index and seed identifiers retain their stable names so the rebrand preserves local data and signed sessions. Native builds use `mn.gerhub.app` and `gerhub://oauth`; existing mobile OAuth clients gain the new callback without changing their stored secret or removing earlier redirects.

| Repository | Responsibility | Primary stack |
| --- | --- | --- |
| `domain-listing-service` | Property listings, media metadata, agent-owned CRUD | Java / Spring Boot / PostgreSQL |
| `domain-identity-service` | Users, roles, OAuth2/OIDC, tokens | Java 17 / Spring Boot / PostgreSQL |
| `domain-search-service` | Property search, facets and geo search | Python / FastAPI / OpenSearch |
| `domain-payment-service` | Subscriptions, featured-listing payments and ledger | Java / Spring Boot / PostgreSQL |
| `domain-web` | SEO-friendly public web and agent dashboard | Next.js / TypeScript |
| `domain-mobile` | Buyer and renter mobile application | React Native / TypeScript |
| `domain-platform-contracts` | Versioned REST and event contracts | OpenAPI / AsyncAPI / JSON Schema |
| `domain-platform-infra` | Local services, persistent data, backup/restore and CI | Docker Compose / GitHub Actions |

Services own their data stores and communicate using versioned contracts. Notification and recommendation services will become separate repositories when their Phase 3 and 4 work begins.

## Run the local platform

Requires Java 17, Maven 3.6.3+, Docker with Compose, OpenSSL, and Node.js 22+.

```bash
mvn -B verify -Pintegration
bash scripts/local.sh init
bash scripts/local.sh up
bash scripts/local.sh seed
bash scripts/local.sh smoke
```

`verify` builds the three executable JARs and runs unit and disposable PostgreSQL integration tests. Use `bash scripts/local.sh build` to rebuild the JARs without running tests before starting the stack.

Open [GerHub](http://localhost:3000). The APIs are [Identity](http://localhost:9000/.well-known/openid-configuration) and [Listing](http://localhost:8080/v1/properties?currency=MNT). Search is localhost:8001, Payment localhost:8081, local SMTP inbox localhost:8025. Databases, Redis and OpenSearch are accessible only inside Compose. HTTP ports bind to loopback. `.local.env` contains private credentials and is ignored by Git. Fresh settings default to `admin@gerhub.local` and `agent@gerhub.local`; existing installations retain their accounts and use the emails/passwords already in that file. Administrators provision agents through the web dashboard; public registration creates buyers.

The interface uses Mongolian, new listings default to MNT, and the public catalog filters MNT explicitly. V5 preserves existing Australian listings and retained responses as AUD without converting their prices. The seed creates six labelled synthetic Mongolian listings; repeated seed runs preserve existing records.

```bash
bash scripts/local.sh status
bash scripts/local.sh logs identity
bash scripts/local.sh stop
```

`stop` preserves all database/index/signing-key volumes. Preserve `.local.env` with those volumes, because regenerated database or OAuth client secrets would differ from the stored settings. Change `BEMON_IDENTITY_PORT` and `BEMON_LISTING_PORT` in `.local.env` if the defaults are occupied.

The smoke check uses real signed tokens and creates labelled synthetic accounts and a listing. New smoke/browser test listings are withdrawn after verification and retained in the database. For frontend development and browser checks, see [the web instructions](domain-web/README.md).

## Readiness and remaining work

See [the readiness report](docs/readiness.md) for validation evidence and limitations. The current deliverable is a working local platform with Mongolian/MNT web and Expo mobile clients. Account/contact/recovery, shared refresh/revocation, favorites/inquiries, full-text/facet/geo search and outbox delivery, saved searches/alerts, payment ledger/featured/subscription flows and Expo Android/iOS bundles are implemented. Real media and external deployment are excluded by the user. Real QPay/SMTP credentials and signed native-device validation remain external checks.
