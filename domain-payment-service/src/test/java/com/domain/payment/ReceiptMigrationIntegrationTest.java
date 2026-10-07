package com.domain.payment;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
/** Verifies append-only upgrades retain receipts from already settled local/provider orders. */
@Testcontainers
class ReceiptMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    /** Applies the old schema with a paid fixture, then checks the current registry backfill. */
    @Test void retainsExistingReceipts(){
        final String url=POSTGRES.getJdbcUrl();final String user=POSTGRES.getUsername();final String password=POSTGRES.getPassword();
        Flyway.configure().dataSource(url,user,password).target("2").load().migrate();
        final var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));final UUID id=UUID.randomUUID();
        db.update("INSERT INTO payment_orders(id,user_id,offer,request_hash,amount,provider,status,callback_hash,receipt_id) VALUES(?,?,'AGENT30',repeat('a',64),99000,'QPAY','PAID',repeat('b',64),'receipt-a,receipt-b')",id,UUID.randomUUID());
        Flyway.configure().dataSource(url,user,password).load().migrate();
        assertThat(db.queryForList("SELECT receipt_id FROM provider_receipts WHERE order_id=? ORDER BY receipt_id",String.class,id)).containsExactly("receipt-a","receipt-b");
        assertThat(db.queryForObject("SELECT status FROM payment_orders WHERE id=?",String.class,id)).isEqualTo("PAID");
        assertThatThrownBy(()->db.update("INSERT INTO provider_receipts(receipt_id,order_id) VALUES('receipt-a',?)",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
