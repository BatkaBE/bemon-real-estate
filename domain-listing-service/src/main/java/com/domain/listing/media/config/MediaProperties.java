package com.domain.listing.media.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Configuration for private property-media object storage. */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        @NotNull Duration presignDuration) {
}
