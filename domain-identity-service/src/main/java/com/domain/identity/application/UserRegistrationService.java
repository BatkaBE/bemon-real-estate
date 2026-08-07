package com.domain.identity.application;

import com.domain.identity.domain.AccountRole;
import com.domain.identity.domain.PlatformUser;
import com.domain.identity.repository.PlatformUserRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for safe account registration and agency-admin provisioning. */
@Service
public class UserRegistrationService {
    private final PlatformUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** Creates the service with account persistence, password encoding, and time dependencies. */
    public UserRegistrationService(
            final PlatformUserRepository users,
            final PasswordEncoder passwordEncoder,
            final Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Registers a public buyer account. Public callers can never select another role. */
    @Transactional
    public PlatformUser registerBuyer(final String email, final String rawPassword) {
        return register(email, rawPassword, AccountRole.ROLE_BUYER);
    }

    /** Provisions an agent account after the API layer authorizes an agency administrator. */
    @Transactional
    public PlatformUser provisionAgent(final String email, final String rawPassword) {
        return register(email, rawPassword, AccountRole.ROLE_AGENT);
    }

    private PlatformUser register(final String email, final String rawPassword, final AccountRole role) {
        final String normalizedEmail = normalizeEmail(email);
        PasswordPolicy.validate(rawPassword);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        try {
            return users.saveAndFlush(PlatformUser.create(
                    normalizedEmail, passwordEncoder.encode(rawPassword), role, clock));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }

    private String normalizeEmail(final String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
