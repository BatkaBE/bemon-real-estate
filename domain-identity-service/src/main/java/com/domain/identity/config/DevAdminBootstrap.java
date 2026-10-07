package com.domain.identity.config;

import com.domain.identity.application.UserRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicitly enabled local-only first-admin provisioning with credentials supplied at runtime. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class DevAdminBootstrap implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevAdminBootstrap.class);
    private final UserRegistrationService registrations;
    private final String email;
    private final String password;

    /** Receives local bootstrap settings without logging the password. */
    public DevAdminBootstrap(final UserRegistrationService registrations,
            @Value("${app.bootstrap.email}") final String email,
            @Value("${app.bootstrap.password}") final String password) {
        this.registrations = registrations;
        this.email = email;
        this.password = password;
    }

    /** Creates the administrator once; later restarts retain the existing password and role. */
    @Override
    public void run(final String... args) {
        final var admin = registrations.bootstrapAdministrator(email, password);
        LOGGER.info("Local administrator available: id={}", admin.getId());
    }
}
