package com.domain.identity.application;

import java.nio.charset.StandardCharsets;

/** Stateless password policy aligned with BCrypt's 72-byte input boundary. */
final class PasswordPolicy {
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Rejects passwords that are too short, too long, or lack required character classes. */
    static void validate(final String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_PASSWORD_BYTES
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new WeakPasswordException();
        }
    }
}
