# Listing API design decisions

This contract applies the backend mastery guide before implementation.

| Guide topic | Decision | Reason |
| --- | --- | --- |
| Module 1.3–1.4 | `/v1` URI versioning, resource-oriented nouns, cursor pagination | Clients can evolve safely and large result sets do not degrade with high offsets. |
| Module 1.3 | Required `Idempotency-Key` on POST | A retry caused by timeout cannot create duplicate listings. |
| Module 3.3–3.4 | Required `If-Match` and ETags on mutations | Prevents a stale agent dashboard from overwriting a newer edit. |
| Module 2.1 | Bearer JWT is required for mutations | Authentication and authorization are explicit at the contract boundary. |
| Module 2.5 | RFC 9457-style problem responses | Validation and authorization failures do not expose stack traces. |
| Module 4.3–4.5 | `property.updated.v1` event and transactional outbox | Search consumes an independent, eventually consistent read model. |

The Listing Service is the only writer for listings. Search, notifications, and recommendations consume events or public APIs; they never query the Listing database directly.
