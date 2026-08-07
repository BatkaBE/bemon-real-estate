package com.domain.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the OAuth2/OIDC Identity bounded context. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IdentityServiceApplication {
    /** Starts the Identity Service. */
    public static void main(final String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
