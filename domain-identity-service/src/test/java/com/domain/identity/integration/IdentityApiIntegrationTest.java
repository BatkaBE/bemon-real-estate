package com.domain.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.domain.identity.application.UserRegistrationService;
import com.domain.identity.domain.AccountRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies account APIs and actual Authorization Code + PKCE tokens against a disposable PostgreSQL. */
@SpringBootTest(properties = "app.oauth.web-client-secret=integration-client-secret")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class IdentityApiIntegrationTest {
    private static final String PASSWORD = "Local#2026Strong";
    private static final String CLIENT_SECRET = "integration-client-secret";
    private static final String REDIRECT = "http://localhost:3000/api/auth/callback/domain";
    private static final String VERIFIER = "a".repeat(64);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRegistrationService registrations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RegisteredClientRepository clients;
    @Autowired private JwtDecoder decoder;

    /** Configures only the isolated test database. */
    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Public JSON registration requires neither a login session nor a CSRF token, and cannot assign roles. */
    @Test
    void registersBuyerAndRejectsDuplicateEmail() throws Exception {
        final String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "role", "ROLE_AGENCY_ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ROLE_BUYER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    /** Interactive login keeps CSRF protection enabled. */
    @Test
    void preservesLoginCsrfProtection() throws Exception {
        mvc.perform(post("/login").param("username", "user@example.test").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    /** Admin APIs return a bearer challenge and forbid buyers from provisioning agents. */
    @Test
    void authorizesAgentProvisioning() throws Exception {
        final String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", PASSWORD));
        mvc.perform(post("/v1/admin/users/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/v1/admin/users/agents").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/users/agents").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_AGENCY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ROLE_AGENT"));
    }

    /** Invalid registration must not include rejected passwords in its problem response. */
    @Test
    void rejectsMultibytePasswordsWithoutLeakingThem() throws Exception {
        final String password = "Aa1#" + "ө".repeat(35);
        mvc.perform(post("/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "invalid-email", "password", password))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(password))));
        mvc.perform(post("/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password))))
                .andExpect(status().isBadRequest());
    }

    /** Browser authorization requests redirect unauthenticated users to the login page. */
    @Test
    void redirectsAuthorizationToLogin() throws Exception {
        mvc.perform(get("/oauth2/authorize").accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code").queryParam("client_id", "domain-web")
                        .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid profile")
                        .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "http://localhost/login"));
    }

    /** Authorization codes are single-use, refresh tokens rotate, and the JDBC state can be reloaded. */
    @Test
    void completesPkceAndPersistsRotatingTokens() throws Exception {
        final String email = UUID.randomUUID() + "@example.test";
        final var buyer = registrations.registerBuyer(email, PASSWORD);
        final var sessionCookies = mvc.perform(formLogin().user(email).password(PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getCookies();
        final String redirect = mvc.perform(get("/oauth2/authorize").cookie(sessionCookies)
                        .queryParam("response_type", "code").queryParam("client_id", "domain-web")
                        .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid profile listings:write")
                        .queryParam("state", "test-state").queryParam("code_challenge", challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        final var parameters = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams();
        assertThat(parameters.getFirst("state")).isEqualTo("test-state");
        final String code = parameters.getFirst("code");
        assertThat(code).isNotBlank();
        final JsonNode tokens = mapper.readTree(mvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("domain-web", CLIENT_SECRET))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        final String accessToken = tokens.get("access_token").asText();
        final String refreshToken = tokens.get("refresh_token").asText();
        assertThat(decoder.decode(accessToken).getSubject()).isEqualTo(buyer.getId().toString());
        assertThat(decoder.decode(accessToken).getClaimAsStringList("roles")).contains("ROLE_BUYER");
        mvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + tokens.get("id_token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(buyer.getId().toString()));
        final var reloaded = new JdbcOAuth2AuthorizationService(jdbc, clients)
                .findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(reloaded).isNotNull();
        final JsonNode rotated = mapper.readTree(mvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("domain-web", CLIENT_SECRET)).param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rotated.get("refresh_token").asText()).isNotEqualTo(refreshToken);
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("domain-web", CLIENT_SECRET))
                        .param("grant_type", "refresh_token").param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("domain-web", CLIENT_SECRET))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest());
    }

    /** Admin bootstrap is idempotent and does not replace the existing password. */
    @Test
    void retainsBootstrapAdministrator() {
        final String email = UUID.randomUUID() + "@example.test";
        final var original = registrations.bootstrapAdministrator(email, PASSWORD);
        final var retained = registrations.bootstrapAdministrator(email, "Different#2026Password");
        assertThat(retained.getId()).isEqualTo(original.getId());
        assertThat(retained.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(retained.getRole()).isEqualTo(AccountRole.ROLE_AGENCY_ADMIN);
    }

    /** Computes the S256 challenge required by the confidential web client. */
    private String challenge() throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
    }
}
