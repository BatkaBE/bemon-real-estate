package com.domain.listing.domain.model;

/** Signals reuse of one idempotency key with a different agent or request body. */
public class IdempotencyConflictException extends RuntimeException {
    /** Creates an exception that avoids returning persisted request details. */
    public IdempotencyConflictException() {
        super("Idempotency-Key was already used for a different request");
    }
}
