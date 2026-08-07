package com.domain.identity.signing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/** Required production configuration for loading the stable JWT signing key. */
@Validated
@ConfigurationProperties(prefix = "app.signing")
public record SigningKeyProperties(
        @NotNull Resource keystoreLocation,
        @NotBlank String keystorePassword,
        @NotBlank String keyAlias,
        @NotBlank String keyPassword,
        @NotBlank String keyId) {
}
