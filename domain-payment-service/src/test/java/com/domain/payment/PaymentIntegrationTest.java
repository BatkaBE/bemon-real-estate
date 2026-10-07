package com.domain.payment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
/** Exercises real PostgreSQL idempotency, money constraints, authorization and entitlement transactions. */
@SpringBootTest(properties={"spring.profiles.active=dev","app.provider=local-test"}) @AutoConfigureMockMvc @Testcontainers
class PaymentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PaymentService service;@Autowired JdbcTemplate db;@Autowired MockMvc mvc;
    @MockitoBean ProviderGateway gateway;@MockitoBean Transport transport;@MockitoBean JwtDecoder decoder;
    private UUID user,property;
    /** Allocates an isolated database instead of changing developer accounts or volumes. */
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    /** Uses actual role conversion with mocked provider transport and signed-token decoding. */
    @BeforeEach void setup(){
        user=UUID.randomUUID();property=UUID.randomUUID();when(gateway.name()).thenReturn("LOCAL_TEST");
        when(gateway.create(any(),any(),any(),any())).thenAnswer(a->Map.of("invoice_id",a.getArgument(0).toString(),"test",true));
        when(transport.send(contains("/eligibility"),any(),any(),any(),any())).thenReturn(Map.of("agentId",user.toString(),"status","ACTIVE","currency","MNT"));
        when(decoder.decode("agent")).thenReturn(token(user,"ROLE_AGENT"));when(decoder.decode("buyer")).thenReturn(token(UUID.randomUUID(),"ROLE_BUYER"));when(decoder.decode("admin")).thenReturn(token(UUID.randomUUID(),"ROLE_AGENCY_ADMIN"));
    }
    /** Identical retries never issue another provider invoice; changed targets conflict. */
    @Test void replayAndConflict(){UUID id=UUID.randomUUID();service.create(user,id,"FEATURED7",property);service.create(user,id,"FEATURED7",property);
        verify(gateway,times(1)).create(eq(id),eq(user),eq(new BigDecimal("20000")),any());assertThatThrownBy(()->service.create(user,id,"AGENT30",null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    /** A lost create response remains durable and is never replayed blindly. */
    @Test void unknownCreation(){UUID id=UUID.randomUUID();doThrow(new IllegalStateException("timeout")).when(gateway).create(eq(id),any(),any(),any());
        assertThat(service.create(user,id,"FEATURED7",property).get("status")).isEqualTo("CREATE_UNKNOWN");service.create(user,id,"FEATURED7",property);verify(gateway,times(1)).create(eq(id),any(),any(),any());
    }
    /** Concurrent confirmations produce one balanced posting and one fixed-expiry grant. */
    @Test void simultaneousConfirmation() throws Exception {UUID id=UUID.randomUUID();service.create(user,id,"FEATURED7",property);final var executor=Executors.newFixedThreadPool(2);
        try{var tasks=executor.invokeAll(List.of(()->service.check(null,id,true),()->service.check(null,id,true)));for(var task:tasks)task.get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
        assertThat(db.queryForObject("SELECT count(*) FROM ledger WHERE order_id=?",Long.class,id)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT sum(amount) FROM ledger WHERE order_id=?",BigDecimal.class,id)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement_outbox WHERE id=?",Long.class,id)).isEqualTo(1);
    }
    /** Subscriptions retain exactly five credits and replay does not consume a second credit. */
    @Test void creditBoundaries(){UUID id=UUID.randomUUID();service.create(user,id,"AGENT30",null);service.check(null,id,true);UUID key=UUID.randomUUID();service.credit(user,key,property);service.credit(user,key,property);
        for(int i=0;i<4;i++)service.credit(user,UUID.randomUUID(),property);
        assertThatThrownBy(()->service.credit(user,UUID.randomUUID(),property)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT remaining FROM subscriptions WHERE id=?",Integer.class,id)).isZero();
    }
    /** Refunds book balancing reversals once and revoke the original grant without deleting history. */
    @Test void refundOnce(){UUID id=UUID.randomUUID();service.create(user,id,"FEATURED7",property);service.check(null,id,true);service.refund(id);service.refund(id);
        assertThat(db.queryForObject("SELECT count(*) FROM ledger WHERE order_id=?",Long.class,id)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT sum(amount) FROM ledger WHERE order_id=?",BigDecimal.class,id)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(db.queryForObject("SELECT revoked FROM entitlement_outbox WHERE id=?",Boolean.class,id)).isTrue();verify(gateway,times(1)).refund(any());
    }
    /** Database constraints reject an incomplete accounting transaction at commit. */
    @Test void rejectsUnbalancedLedger(){UUID id=UUID.randomUUID();service.create(user,id,"FEATURED7",property);
        assertThatThrownBy(()->db.update("INSERT INTO ledger(id,order_id,kind,account,amount) VALUES(?,?,'SALE','BAD',1)",UUID.randomUUID(),id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    /** Anonymous/buyer purchases, private order reads and forged callbacks are denied. */
    @Test void authorization() throws Exception {UUID id=UUID.randomUUID();service.create(user,id,"FEATURED7",property);
        mvc.perform(post("/v1/payments/orders").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/payments/orders").header("Authorization","Bearer buyer").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"offer\":\"AGENT30\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/v1/payments/orders/{id}",id).header("Authorization","Bearer buyer")).andExpect(status().isNotFound());
        mvc.perform(get("/v1/payments/callback/{id}",id).param("token","forged")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/payments/{id}/simulate",id).header("Authorization","Bearer buyer")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/payments/{id}/simulate",id).header("Authorization","Bearer admin")).andExpect(status().isOk());
    }
    /** Supplies immutable account subjects and role claims. */
    private Jwt token(UUID id,String role){return Jwt.withTokenValue("fixture").header("alg","RS256").subject(id.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("roles",List.of(role)).build();}
}
