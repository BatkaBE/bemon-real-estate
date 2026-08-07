package com.domain.listing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides time dependencies explicitly so business rules remain testable.
 */
@Configuration
public class TimeConfiguration {

    /**
     * Supplies the UTC application clock.
     *
     * @return system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
