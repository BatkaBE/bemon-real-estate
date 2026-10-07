package com.domain.listing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.domain.listing.application.PropertyApplicationService;
import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import com.domain.listing.domain.model.PropertySnapshot;
import com.domain.listing.domain.model.PropertyStatus;
import com.domain.listing.domain.model.PropertyType;
import com.domain.listing.domain.model.PriceCurrency;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises real HTTP authorization, PostgreSQL constraints, and transaction boundaries. */
@SpringBootTest(properties="app.bounds.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class ListingApiIntegrationTest {
    private static final String BODY = """
            {"title":"House","propertyType":"HOUSE","listingType":"SALE","price":100.00,
             "bedrooms":2,"bathrooms":1,"parkingSpaces":0,
             "address":{"addressLine":"1 Example Street","suburb":"Richmond","state":"VIC",
                        "postcode":"3121","latitude":-37.8,"longitude":145.0}}
            """;
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired private MockMvc mvc;
    @Autowired private PropertyApplicationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private com.domain.listing.application.IdentityBridge identity;
    @Autowired private com.domain.listing.application.EngagementService engagement;
    private UUID buyerId;
    private UUID agentId;

    /** Uses a fresh disposable database instead of any developer database. */
    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Supplies distinct signed-token identities while retaining the application's authority converter. */
    @BeforeEach
    void identities() {
        agentId = UUID.randomUUID();
        when(jwtDecoder.decode("agent")).thenReturn(token("agent", agentId, "ROLE_AGENT"));
        when(jwtDecoder.decode("other")).thenReturn(token("other", UUID.randomUUID(), "ROLE_AGENT"));
        buyerId=UUID.randomUUID();
        when(jwtDecoder.decode("buyer")).thenReturn(token("buyer", buyerId, "ROLE_BUYER"));
        when(identity.profile("buyer")).thenReturn(java.util.Map.of("id",buyerId.toString(),"displayName","Buyer","email","buyer@example.test","phone",""));
    }

    /** Both authentication and role authorization failures use Problem Details. */
    @Test
    void rejectsUnauthenticatedAndBuyerWrites() throws Exception {
        mvc.perform(post("/v1/properties").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.traceId").exists());
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer buyer")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)).andExpect(status().isForbidden());
    }

    /** Only the draft's owner can view or replace it; public details appear after publishing. */
    @Test
    void protectsDraftsAndOwnership() throws Exception {
        final PropertySnapshot property = create();
        mvc.perform(get("/v1/properties/{id}", property.id())).andExpect(status().isNotFound());
        mvc.perform(get("/v1/properties/{id}", property.id()).header("Authorization", "Bearer other"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/properties/{id}", property.id()).header("Authorization", "Bearer agent"))
                .andExpect(status().isOk());
        mvc.perform(put("/v1/properties/{id}", property.id()).header("Authorization", "Bearer other")
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        service.changeStatus(agentId, property.id(), property.version(), PropertyStatus.ACTIVE);
        mvc.perform(get("/v1/properties/{id}", property.id())).andExpect(status().isOk());
        service.changeStatus(agentId, property.id(), property.version() + 1, PropertyStatus.WITHDRAWN);
        mvc.perform(get("/v1/properties/{id}", property.id())).andExpect(status().isNotFound());
        mvc.perform(get("/v1/properties/{id}", property.id()).header("Authorization", "Bearer agent"))
                .andExpect(status().isOk());
    }

    /** A changed request using an existing key returns 409 rather than an exception-handler failure. */
    @Test
    void reportsIdempotencyConflict() throws Exception {
        final UUID key = UUID.randomUUID();
        service.create(agentId, key, draft());
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"House\"", "\"Changed house\"")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    /** Retried create responses do not expose later mutations or increment the outbox. */
    @Test
    void retainsOriginalResponseAfterPublishing() {
        final UUID key = UUID.randomUUID();
        final PropertySnapshot first = service.create(agentId, key, draft());
        service.changeStatus(agentId, first.id(), first.version(), PropertyStatus.ACTIVE);
        final PropertySnapshot retry = service.create(agentId, key, draft());
        assertThat(retry).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                Long.class, first.id())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT max((payload->>'aggregateVersion')::bigint) "
                + "FROM outbox_events WHERE aggregate_id = ?", Long.class, first.id())).isEqualTo(1L);
    }

    /** Concurrent retries share one committed listing and event. */
    @Test
    void serializesConcurrentCreates() throws Exception {
        final int workers = 4;
        final UUID key = UUID.randomUUID();
        final CountDownLatch start = new CountDownLatch(1);
        final var executor = Executors.newFixedThreadPool(workers);
        try {
            final List<Future<PropertySnapshot>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.create(agentId, key, draft());
                }));
            }
            start.countDown();
            final UUID id = futures.get(0).get(20, TimeUnit.SECONDS).id();
            for (final var future : futures) {
                assertThat(future.get(20, TimeUnit.SECONDS).id()).isEqualTo(id);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM properties WHERE agent_id = ?",
                    Long.class, agentId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                    Long.class, id)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Expired keys can create another listing while preserving the first listing. */
    @Test
    void honorsIdempotencyExpiry() {
        final UUID key = UUID.randomUUID();
        final PropertySnapshot first = service.create(agentId, key, draft());
        jdbc.update("UPDATE idempotency_records SET expires_at = now() - interval '1 second' "
                + "WHERE idempotency_key = ?", key);
        final PropertySnapshot second = service.create(agentId, key, draft());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(service.create(agentId, key, draft()).id()).isEqualTo(second.id());
    }

    /** Unexpired pre-upgrade records still replay without creating another property. */
    @Test
    void replaysLegacyRecords() {
        final UUID key = UUID.randomUUID();
        final PropertySnapshot original = service.create(agentId, key, draft());
        final String legacyHash = "cec9decaf2ef14c548432ba44a5a365373cac6fc63c1dcf7d2052d202072a810";
        jdbc.update("UPDATE idempotency_records SET response_body = NULL, request_hash = ? "
                + "WHERE idempotency_key = ?", legacyHash, key);
        assertThat(service.create(agentId, key, draft()).id()).isEqualTo(original.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM properties WHERE agent_id = ?",
                Long.class, agentId)).isEqualTo(1L);
    }

    /** ETags advance on publish and stale updates return 412 with the same trace identifier. */
    @Test
    void advancesVersionAndRejectsStaleUpdates() throws Exception {
        final PropertySnapshot property = create();
        mvc.perform(patch("/v1/properties/{id}/status", property.id()).header("Authorization", "Bearer agent")
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(service.get(property.id(), null).getUpdatedAt()).isAfter(property.updatedAt());
        mvc.perform(put("/v1/properties/{id}", property.id()).header("Authorization", "Bearer agent")
                        .header("If-Match", "\"0\"").header("X-Request-Id", "stale-request")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string("X-Request-Id", "stale-request"))
                .andExpect(jsonPath("$.traceId").value("stale-request"));
    }

    /** Invalid cursor timestamps and out-of-range filters consistently return 400. */
    @Test
    void rejectsInvalidFiltersAndCursors() throws Exception {
        final String cursor = Base64.getUrlEncoder().encodeToString(
                ("invalid-date|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/v1/properties").param("cursor", cursor)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/properties").param("minPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/properties").param("minBedrooms", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/properties").param("pageSize", "101")).andExpect(status().isBadRequest());
    }

    /** Malformed JSON, missing headers, and numeric overflow do not leak rejected bodies or reach the database. */
    @Test
    void validatesRequestBoundaries() throws Exception {
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").exists());
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("100.00", "10000000000.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"sensitive-invalid-body\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive-invalid-body"))));
    }

    /** Disabled object storage is explicit and does not leave pending rows behind. */
    @Test
    void rollsBackUnavailableMedia() throws Exception {
        final PropertySnapshot property = create();
        mvc.perform(post("/v1/properties/{id}/media/upload-url", property.id())
                        .header("Authorization", "Bearer agent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1024,\"displayOrder\":0}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_media WHERE property_id = ?",
                Long.class, property.id())).isZero();
    }

    /** Two simultaneous editors cannot both commit a replacement with the same ETag. */
    @Test
    void rejectsConcurrentLostUpdates() throws Exception {
        final PropertySnapshot property = create();
        final CountDownLatch start = new CountDownLatch(1);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<Integer>> responses = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                final String title = "Editor " + index;
                responses.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(put("/v1/properties/{id}", property.id())
                                    .header("Authorization", "Bearer agent").header("If-Match", "\"0\"")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(BODY.replace("\"House\"", "\"" + title + "\"")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(List.of(responses.get(0).get(20, TimeUnit.SECONDS),
                    responses.get(1).get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                    Long.class, property.id())).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Equal timestamps still paginate by UUID without missing or repeating a listing. */
    @Test
    void paginatesPublishedListingsWithTiedTimestamps() throws Exception {
        final String suburb = UUID.randomUUID().toString();
        final PropertyDraft draft = new PropertyDraft("House", PropertyType.HOUSE, ListingType.SALE,
                new BigDecimal("100.00"), 2, 1, 0, null,
                new PropertyAddress("1 Example Street", suburb, "VIC", "3121", -37.8, 145.0));
        final var first = service.create(agentId, UUID.randomUUID(), draft);
        final var second = service.create(agentId, UUID.randomUUID(), draft);
        service.changeStatus(agentId, first.id(), first.version(), PropertyStatus.ACTIVE);
        service.changeStatus(agentId, second.id(), second.version(), PropertyStatus.ACTIVE);
        jdbc.update("UPDATE properties SET created_at = '2026-10-05T01:02:03Z' WHERE agent_id = ?", agentId);
        final var page = mapper.readTree(mvc.perform(get("/v1/properties").param("suburb", suburb)
                        .param("pageSize", "1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        final var next = mapper.readTree(mvc.perform(get("/v1/properties").param("suburb", suburb)
                        .param("pageSize", "1").param("cursor", page.get("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(List.of(page.get("items").get(0).get("id").asText(),
                next.get("items").get(0).get("id").asText()))
                .containsExactlyInAnyOrder(first.id().toString(), second.id().toString());
        assertThat(next.get("nextCursor").isNull()).isTrue();
    }


    /** Favorites are caller-scoped and withdrawn listings disappear without deleting saved records. */
    @Test void favoritesRespectVisibility() throws Exception {
        final var p=create();
        mvc.perform(put("/v1/users/me/favorites/{id}",p.id()).header("Authorization","Bearer buyer")).andExpect(status().isNotFound());
        service.changeStatus(agentId,p.id(),0,PropertyStatus.ACTIVE);
        engagement.favorite(buyerId,p.id());engagement.favorite(buyerId,p.id());
        assertThat(engagement.favorites(buyerId,null).get("items")).asList().hasSize(1);
        assertThat(engagement.favorites(UUID.randomUUID(),null).get("items")).asList().isEmpty();
        service.changeStatus(agentId,p.id(),1,PropertyStatus.WITHDRAWN);
        assertThat(engagement.favorites(buyerId,null).get("items")).asList().isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id=?",Long.class,buyerId)).isEqualTo(1);
    }
    /** Conversations use verified contact data, deduplicate retries and reject stale or foreign replies. */
    @Test void inquiriesAndReplies() throws Exception {
        final var p=create();service.changeStatus(agentId,p.id(),0,PropertyStatus.ACTIVE);final UUID key=UUID.randomUUID();
        final var first=engagement.inquire(buyerId,p.id(),key,"Hello","buyer");
        assertThat(engagement.inquire(buyerId,p.id(),key,"Hello","buyer")).isEqualTo(first);
        assertThat(first).doesNotContainKey("requestHash");
        assertThat(engagement.inquiries(UUID.randomUUID(),true,null).get("items")).asList().isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->engagement.inquire(buyerId,p.id(),key,"changed","buyer")).isInstanceOf(com.domain.listing.domain.model.IdempotencyConflictException.class);
        mvc.perform(patch("/v1/inquiries/{id}",key).header("Authorization","Bearer other").header("If-Match","\"0\"").contentType("application/json").content("{\"status\":\"CONTACTED\",\"reply\":\"Reply\"}")).andExpect(status().isForbidden());
        engagement.reply(agentId,key,0,"CONTACTED","Reply");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->engagement.reply(agentId,key,0,"CLOSED","old")).isInstanceOf(com.domain.listing.domain.model.StalePropertyVersionException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE user_id IN (?,?)",Long.class,agentId,buyerId)).isEqualTo(2);
    }
    /** Chunked JSON is bounded before parsing and shared buckets limit repeated writes. */
    @Test void requestBounds() throws Exception {
        mvc.perform(post("/v1/properties").contentType("application/json").content("x".repeat(65537))).andExpect(status().isPayloadTooLarge());
        jdbc.update("INSERT INTO request_buckets(bucket_key,window_start,request_count) VALUES('writes:127.0.0.1',now(),119) ON CONFLICT(bucket_key) DO UPDATE SET window_start=now(),request_count=119");
        try {
            mvc.perform(post("/v1/properties").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(post("/v1/properties").contentType("application/json").content("{}")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        } finally {jdbc.update("DELETE FROM request_buckets WHERE bucket_key='writes:127.0.0.1'");}
    }
    /** Creates one private property using a fresh key. */
    private PropertySnapshot create() {
        return service.create(agentId, UUID.randomUUID(), draft());
    }

    /** Private browsing derives ownership from the JWT and does not trust query-string IDs. */
    @Test
    void browsesOnlyOwnedListingsAcrossStatuses() throws Exception {
        final var first = create();
        final var second = create();
        service.changeStatus(agentId, second.id(), second.version(), PropertyStatus.ACTIVE);
        service.create(UUID.randomUUID(), UUID.randomUUID(), draft());
        mvc.perform(get("/v1/agents/me/properties")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/agents/me/properties").header("Authorization", "Bearer buyer"))
                .andExpect(status().isForbidden());
        final var page = mapper.readTree(mvc.perform(get("/v1/agents/me/properties")
                        .header("Authorization", "Bearer agent").param("agentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("items").findValuesAsText("id"))
                .containsExactlyInAnyOrder(first.id().toString(), second.id().toString());
        assertThat(page.get("items").findValuesAsText("agentId")).containsOnly(agentId.toString());
    }

    /** New drafts use MNT and currency is part of the idempotency fingerprint. */
    @Test
    void defaultsToMntAndRejectsCurrencyChangingRetry() throws Exception {
        final UUID key = UUID.randomUUID();
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currency").value("MNT"));
        mvc.perform(post("/v1/properties").header("Authorization", "Bearer agent")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"title\":", "\"currency\":\"AUD\",\"title\":")))
                .andExpect(status().isConflict());
    }

    /** Existing AUD amounts retain their label when an old client omits the new field. */
    @Test
    void preservesAudOnReplacementAndFiltersCatalog() throws Exception {
        final var original = draft();
        final var aud = service.create(agentId, UUID.randomUUID(), new PropertyDraft(original.title(),
                original.propertyType(), original.listingType(), original.price(), original.bedrooms(),
                original.bathrooms(), original.parkingSpaces(), original.landSizeSqm(), original.address(), PriceCurrency.AUD));
        mvc.perform(put("/v1/properties/{id}", aud.id()).header("Authorization", "Bearer agent")
                        .header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.price").value(100.00));
        service.changeStatus(agentId, aud.id(), 1, PropertyStatus.ACTIVE);
        final var page = mapper.readTree(mvc.perform(get("/v1/properties").param("currency", "MNT")
                        .param("pageSize", "100")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.get("items").findValuesAsText("id")).doesNotContain(aud.id().toString());
        assertThat(page.get("items").findValuesAsText("currency")).doesNotContain("AUD");
        mvc.perform(get("/v1/properties").param("currency", "USD")).andExpect(status().isBadRequest());
    }

    /** Pre-currency snapshots replay their original denomination even after the current listing changes. */
    @Test
    void replaysPreviousFingerprintAfterCurrencyReplacement() {
        final var original = draft();
        final var aud = original.withDefaultCurrency(PriceCurrency.AUD);
        // A non-null draft denomination is explicit, so build the legacy AUD request separately.
        final var previous = new PropertyDraft(aud.title(), aud.propertyType(), aud.listingType(), aud.price(),
                aud.bedrooms(), aud.bathrooms(), aud.parkingSpaces(), aud.landSizeSqm(), aud.address(), PriceCurrency.AUD);
        final UUID key = UUID.randomUUID();
        final var response = service.create(agentId, key, previous);
        final String previousHash = "f0c5cda3cb6564e41ac132dc6b502987b0fb025083e5c153e7f9eb8edca816c0";
        jdbc.update("UPDATE idempotency_records SET request_hash = ? WHERE idempotency_key = ?", previousHash, key);
        service.update(agentId, response.id(), response.version(), original);
        assertThat(service.create(agentId, key, previous)).isEqualTo(response);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(agentId, key, original))
                .isInstanceOf(com.domain.listing.domain.model.IdempotencyConflictException.class);
    }

    /** Supplies a draft matching the API fixture. */
    private PropertyDraft draft() {
        return new PropertyDraft("House", PropertyType.HOUSE, ListingType.SALE, new BigDecimal("100.00"),
                2, 1, 0, null, new PropertyAddress("1 Example Street", "Richmond", "VIC", "3121", -37.8, 145.0));
    }

    /** Builds a decoded JWT fixture with real scope and role claims. */
    private Jwt token(final String value, final UUID subject, final String role) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).claim("scope", "listings:write").build();
    }
}
