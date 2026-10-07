package com.domain.identity.application;

/** Signals a password that does not meet the platform's minimum security policy. */
public class WeakPasswordException extends RuntimeException {
    /** Creates a generic password-policy exception. */
    public WeakPasswordException() {
        super("Password must contain at least 12 characters, fit in 72 UTF-8 bytes, "
                + "and include uppercase, lowercase, digit, and symbol");
    }
}
