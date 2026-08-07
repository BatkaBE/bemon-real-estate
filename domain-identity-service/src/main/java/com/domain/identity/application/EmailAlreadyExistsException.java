package com.domain.identity.application;

/** Signals a duplicate account email without exposing any existing-account data. */
public class EmailAlreadyExistsException extends RuntimeException {
    /** Creates a duplicate-email exception. */
    public EmailAlreadyExistsException() {
        super("An account with this email already exists");
    }
}
