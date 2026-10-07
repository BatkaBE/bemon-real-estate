package com.domain.listing.persistence;

import com.domain.listing.domain.model.PropertySnapshot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private PropertySnapshot responseBody;

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
            final Instant expiresAt,
            final PropertySnapshot responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.agentId = agentId;
        this.requestHash = requestHash;
        this.propertyId = propertyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.responseBody = responseBody;
    }

    /** Checks whether a request safely matches this stored replay record. */
    public boolean matches(final UUID candidateAgentId, final String candidateRequestHash) {
        return agentId.equals(candidateAgentId) && requestHash.equals(candidateRequestHash);
    }

    /** @return the previously created property identifier. */
    public UUID getPropertyId() {
        return propertyId;
    }

    /** Returns the original create response, even if the listing has since changed. */
    public PropertySnapshot getResponseBody() {
        return responseBody;
    }

    /** Returns whether this key's retention window has ended. */
    public boolean isExpired(final Instant now) {
        return !expiresAt.isAfter(now);
    }
}
