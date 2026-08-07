package com.domain.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Verifies password-policy boundaries without framework or database dependencies. */
class PasswordPolicyTest {
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
