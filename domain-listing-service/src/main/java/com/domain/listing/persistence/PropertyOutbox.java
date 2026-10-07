package com.domain.listing.persistence;

import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertySnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists contract-compliant events atomically with listing mutations; delivery is a later worker. */
@Component
public class PropertyOutbox {
    private static final String EVENT_TYPE = "property.updated.v1";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Shares the listing transaction and application's configured JSON mapper and clock. */
    public PropertyOutbox(final JdbcTemplate jdbcTemplate, final ObjectMapper objectMapper, final Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Stores a full projection only after the aggregate's version has been flushed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(final Property property) {
        final UUID eventId = UUID.randomUUID();
        final Instant occurredAt = Instant.now(clock);
        final PropertyEvent event = new PropertyEvent(eventId, EVENT_TYPE, occurredAt,
                property.getId(), property.getVersion(), PropertySnapshot.from(property));
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox_events
                        (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                    VALUES (?, 'Property', ?, ?, CAST(? AS jsonb), ?)
                    """, eventId, property.getId(), EVENT_TYPE, objectMapper.writeValueAsString(event),
                    java.sql.Timestamp.from(occurredAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize property event", exception);
        }
    }

    /** Event envelope defined by property-events-v1.yaml. */
    private record PropertyEvent(UUID eventId, String eventType, Instant occurredAt,
            UUID aggregateId, long aggregateVersion, PropertySnapshot data) { }
}
