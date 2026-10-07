package com.domain.listing.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes requests for the same key across service replicas, including the first insert. */
@Component
public class IdempotencyLock {
    private final JdbcTemplate jdbcTemplate;

    /** Uses the same datasource and transaction as listing persistence. */
    public IdempotencyLock(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Holds a PostgreSQL advisory lock until the surrounding transaction commits or rolls back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(final UUID key) {
        final long lockId = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> { }, lockId);
    }
}
