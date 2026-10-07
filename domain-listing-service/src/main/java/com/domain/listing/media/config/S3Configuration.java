package com.domain.listing.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Builds the AWS SDK presigner using the runtime's default credential provider chain. */
@Configuration
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class S3Configuration {
    /** Creates a thread-safe S3 presigner for short-lived direct uploads. */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(final MediaProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }
}
