package com.domain.listing.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Stable keyset position for listings sorted by created time and identifier descending. */
public record PropertyCursor(Instant createdAt, UUID propertyId) {
}
