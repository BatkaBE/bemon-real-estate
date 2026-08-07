package com.domain.identity.application;

/** Signals a password that does not meet the platform's minimum security policy. */
public class WeakPasswordException extends RuntimeException {
    /** Creates a generic password-policy exception. */
    public WeakPasswordException() {
        super("Password must contain 12 to 72 characters, uppercase, lowercase, digit, and symbol");
    }
}
