package com.domain.identity.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.domain.identity.application.AccountService;
import com.domain.identity.application.UserRegistrationService;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves recovery, verification, privacy, and cross-worker session leases against PostgreSQL. */
@SpringBootTest(properties={"app.oauth.web-client-secret=test-secret","app.internal-key=test-internal-key"})
@AutoConfigureMockMvc @ActiveProfiles("dev") @Testcontainers
class AccountIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired AccountService accounts;
    @Autowired UserRegistrationService users;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    /** Uses a disposable database, never the local development volumes. */
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl);registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
    }
    /** Returns a synthetic challenge from the undelivered private test outbox. */
    private String token(String email) {
        final String body=db.queryForObject("SELECT body FROM email_outbox WHERE recipient=? ORDER BY created_at DESC LIMIT 1",String.class,email);
        return body.substring(body.indexOf("?token=")+7);
    }
    /** Verification is hashed, expiring, purpose-bound, and single-use. */
    @Test void verifiesEmailOnceAndRejectsExpiredChallenges() {
        final var user=users.registerBuyer(UUID.randomUUID()+"@example.test","Strong#2026Password");
        accounts.requestVerification(user.getId());final String value=token(user.getEmail());
        assertThat(db.queryForObject("SELECT token_hash FROM account_tokens WHERE user_id=?",String.class,user.getId())).doesNotContain(value);
        assertThatThrownBy(()->accounts.reset(value,"Another#2026Password")).isInstanceOf(IllegalArgumentException.class);
        accounts.verify(value);assertThat(accounts.profile(user.getId()).get("emailVerified")).isEqualTo(true);
        assertThatThrownBy(()->accounts.verify(value)).isInstanceOf(IllegalArgumentException.class);
        accounts.requestReset(user.getEmail());db.update("UPDATE account_tokens SET expires_at=now()-interval '1 second' WHERE user_id=?",user.getId());
        assertThatThrownBy(()->accounts.reset(token(user.getEmail()),"Another#2026Password")).isInstanceOf(IllegalArgumentException.class);
    }
    /** Two concurrent recovery requests cannot both consume the same token. */
    @Test void serializesConcurrentResetAndRevokesDurableSessions() throws Exception {
        final var user=users.registerBuyer(UUID.randomUUID()+"@example.test","Strong#2026Password");
        db.update("INSERT INTO web_sessions(id,user_id,payload,expires_at) VALUES(?,?,?,now()+interval '1 day')",UUID.randomUUID(),user.getId(),"encrypted-test");
        accounts.requestReset(user.getEmail());final String value=token(user.getEmail());
        final var pool=Executors.newFixedThreadPool(2);
        try {
            final java.util.concurrent.Callable<Boolean> reset=()-> {try{accounts.reset(value,"Changed#2026Password");return true;}catch(IllegalArgumentException used){return false;}};
            final var one=pool.submit(reset);final var two=pool.submit(reset);
            assertThat(java.util.List.of(one.get(),two.get())).containsExactlyInAnyOrder(true,false);
        } finally {pool.shutdownNow();}
        assertThat(accounts.authVersion(user.getId())).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM web_sessions WHERE user_id=?",Integer.class,user.getId())).isZero();
    }
    /** Anonymous recovery outcomes do not disclose accounts; profile writes cannot elevate roles. */
    @Test void protectsProfileAndServiceEndpoints() throws Exception {
        final var user=users.registerBuyer(UUID.randomUUID()+"@example.test","Strong#2026Password");
        mvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/users/me").with(jwt().jwt(jwt->jwt.subject(user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Бат\",\"phone\":\"99112233\",\"role\":\"ROLE_AGENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ROLE_BUYER"));
        mvc.perform(get("/v1/agents/{id}/contact",user.getId())).andExpect(status().isOk()).andExpect(content().json("{}"));
        mvc.perform(get("/internal/accounts/{id}/version",user.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/v1/accounts/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\"}")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM email_outbox WHERE recipient='unknown@example.test'",Integer.class)).isZero();
    }
}
