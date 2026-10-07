package com.domain.listing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.domain.listing.application.PropertyApplicationService;
import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.PropertySnapshot;
import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import com.domain.listing.domain.model.PropertyType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies idempotent creation against a real PostgreSQL database and Flyway schema. */
@SpringBootTest
@Testcontainers
class IdempotencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PropertyApplicationService propertyApplicationService;

    /** Supplies Testcontainers database settings before Spring builds the application context. */
    @DynamicPropertySource
    static void configureDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** A timeout retry with the same key returns the original property rather than creating another. */
    @Test
    void repeatedCreateReturnsOriginalProperty() {
        final UUID agentId = UUID.randomUUID();
        final UUID idempotencyKey = UUID.randomUUID();
        final PropertyDraft draft = new PropertyDraft(
                "Light-filled townhouse", PropertyType.TOWNHOUSE, ListingType.SALE,
                new BigDecimal("925000.00"), 3, 2, 1, new BigDecimal("180"),
                new PropertyAddress("10 Example Street", "Richmond", "VIC", "3121",
                        -37.8183, 144.9985));

        final PropertySnapshot first = propertyApplicationService.create(agentId, idempotencyKey, draft);
        final PropertySnapshot retry = propertyApplicationService.create(agentId, idempotencyKey, draft);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.agentId()).isEqualTo(agentId);
    }
}
