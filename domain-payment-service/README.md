# Domain Payment Service

Owns subscriptions, featured-listing purchases, payment provider integration, and the double-entry ledger.

**Stack:** Java 21, Spring Boot, PostgreSQL.

All money-changing requests require an idempotency key and auditable ledger records.
