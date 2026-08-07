package com.domain.identity.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a testable UTC clock for user lifecycle use cases. */
@Configuration
public class TimeConfiguration {
    /** Returns the application-wide UTC clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
