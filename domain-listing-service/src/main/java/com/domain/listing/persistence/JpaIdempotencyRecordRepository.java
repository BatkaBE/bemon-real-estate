package com.domain.listing.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementation detail for persisted idempotency records. */
public interface JpaIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
}
