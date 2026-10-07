package com.domain.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Verifies password-policy boundaries without framework or database dependencies. */
class PasswordPolicyTest {
    /** Multibyte input must respect BCrypt's byte limit even below 72 characters. */
    @Test
    void rejectsOverlongUtf8Password() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Aa1#" + "ө".repeat(35)))
                .isInstanceOf(WeakPasswordException.class);
    }

    /** Exactly 72 UTF-8 bytes remain usable. */
    @Test
    void acceptsExactUtf8ByteLimit() {
        assertThatCode(() -> PasswordPolicy.validate("Aa1#" + "ө".repeat(34)))
                .doesNotThrowAnyException();
    }

    /** A 12-character password containing all required character classes is accepted. */
    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> PasswordPolicy.validate("Domain#2026Aa"))
                .doesNotThrowAnyException();
    }

    /** A short password is rejected even when it has diverse characters. */
    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("D#2a"))
                .isInstanceOf(WeakPasswordException.class);
    }

    /** A password lacking a symbol is rejected. */
    @Test
    void rejectsPasswordWithoutSymbol() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Domain2026Aa"))
                .isInstanceOf(WeakPasswordException.class);
    }
}
