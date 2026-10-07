# GerHub Platform Infrastructure

Infrastructure, deployment, and observability definitions for the platform.

**Target:** AWS with EKS, RDS PostgreSQL, ElastiCache Redis, OpenSearch, S3, and CloudFront.

The current implementation is `compose.local.yaml`, orchestrated by `../scripts/local.sh`, with Identity, Listing, Payment, Search, Next.js web, four isolated PostgreSQL volumes, OpenSearch, Redis and local Mailpit SMTP. All HTTP host ports bind to loopback; database/index ports stay private. The development RSA key persists in its own volume. `../scripts/backup-restore-local.sh` backs up and verifies all four databases in a new temporary container without altering their source volumes.

Backend, web, activated-Conda search, mobile bundle and real-stack Playwright CI jobs are defined in `../.github/workflows/backend.yml`; remote CI execution is not claimed.

Terraform, Kubernetes, production secrets, HTTPS ingress, and production observability remain future work. The local stack enables the Identity `dev` profile and is intended for local development only.
