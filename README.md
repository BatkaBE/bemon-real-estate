# Domain Platform Workspace

This directory groups independently versioned repositories for the real-estate platform.

| Repository | Responsibility | Primary stack |
| --- | --- | --- |
| `domain-listing-service` | Property listings, media metadata, agent-owned CRUD | Java / Spring Boot / PostgreSQL |
| `domain-identity-service` | Users, roles, OAuth2/OIDC, tokens | Java / Spring Boot / PostgreSQL |
| `domain-search-service` | Property search, facets and geo search | Python / FastAPI / OpenSearch |
| `domain-payment-service` | Subscriptions, featured-listing payments and ledger | Java / Spring Boot / PostgreSQL |
| `domain-web` | SEO-friendly public web and agent dashboard | Next.js / TypeScript |
| `domain-mobile` | Buyer and renter mobile application | React Native / TypeScript |
| `domain-platform-contracts` | Versioned REST and event contracts | OpenAPI / AsyncAPI / JSON Schema |
| `domain-platform-infra` | AWS, Kubernetes and CI/CD definitions | Terraform / Helm / GitHub Actions |

Services own their data stores and communicate using versioned contracts. Notification and recommendation services will become separate repositories when their Phase 3 and 4 work begins.
