package com.domain.listing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the property Listing bounded context.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ListingServiceApplication {

    /**
     * Starts the Listing Service application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(ListingServiceApplication.class, args);
    }
}
