package com.domain.listing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Rehearses the currency upgrade against retained V4 rows in a disposable database. */
@Testcontainers
class CurrencyMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Existing prices and replay/event JSON remain AUD while new SQL rows default to MNT. */
    @Test
    void preservesLegacyCurrencyWithoutConvertingAmounts() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("4").load().migrate();
        try (final var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); final var sql = connection.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO properties(id,agent_id,title,property_type,listing_type,price,address_line,
                      suburb,state,postcode,latitude,longitude,status,created_at,updated_at)
                    VALUES('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000002',
                      'Legacy','HOUSE','SALE',123456.78,'1 Example','Richmond','VIC','3121',-37.8,145,
                      'DRAFT',now(),now())
                    """);
            sql.executeUpdate("""
                    INSERT INTO idempotency_records(idempotency_key,agent_id,request_hash,response_body,created_at,expires_at,property_id)
                    VALUES('00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-000000000002',
                      repeat('a',64),'{"price":123456.78}',now(),now()+interval '1 day',
                      '00000000-0000-4000-8000-000000000001')
                    """);
            sql.executeUpdate("""
                    INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload,occurred_at)
                    VALUES('00000000-0000-4000-8000-000000000004','Property',
                      '00000000-0000-4000-8000-000000000001','property.updated.v1',
                      '{"data":{"price":123456.78}}',now())
                    """);
            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load().migrate();
            try (final var result = sql.executeQuery("""
                    SELECT p.currency,p.price,i.response_body->>'currency',o.payload->'data'->>'currency'
                    FROM properties p CROSS JOIN idempotency_records i CROSS JOIN outbox_events o
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("AUD");
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo("123456.78");
                assertThat(result.getString(3)).isEqualTo("AUD");
                assertThat(result.getString(4)).isEqualTo("AUD");
            }
            try (final var result = sql.executeQuery("SELECT column_default FROM information_schema.columns "
                    + "WHERE table_name='properties' AND column_name='currency'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).contains("MNT");
            }
        }
    }
}
