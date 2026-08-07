package com.domain.listing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persisted replay record for one idempotent create request. */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Required by JPA. */
    protected IdempotencyRecord() {
    }

    /** Creates a replay record that remains valid for the supplied retention period. */
    public IdempotencyRecord(
            final UUID idempotencyKey,
            final UUID agentId,
            final String requestHash,
            final UUID propertyId,
            final Instant createdAt,
            final Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.agentId = agentId;
        this.requestHash = requestHash;
        this.propertyId = propertyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Checks whether a request safely matches this stored replay record. */
    public boolean matches(final UUID candidateAgentId, final String candidateRequestHash) {
        return agentId.equals(candidateAgentId) && requestHash.equals(candidateRequestHash);
    }

    /** @return the previously created property identifier. */
    public UUID getPropertyId() {
        return propertyId;
    }
}
