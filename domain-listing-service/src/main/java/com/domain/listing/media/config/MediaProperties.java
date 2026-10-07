package com.domain.listing.media.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

/** Configuration for private property-media object storage. */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        @NotNull Duration presignDuration) {
    private static final Duration MAXIMUM_PRESIGN_DURATION = Duration.ofMinutes(10);

    /** Bounds upload capabilities to a positive duration no longer than ten minutes. */
    @AssertTrue(message = "presignDuration must be positive and no longer than PT10M")
    public boolean isPresignDurationValid() {
        return presignDuration != null && !presignDuration.isZero() && !presignDuration.isNegative()
                && presignDuration.compareTo(MAXIMUM_PRESIGN_DURATION) <= 0;
    }
}
