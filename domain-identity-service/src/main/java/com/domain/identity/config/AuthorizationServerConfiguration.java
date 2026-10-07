package com.domain.identity.config;

import com.domain.identity.repository.PlatformUserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
import com.domain.identity.web.error.ApiSecurityErrors;
import com.domain.identity.application.AccountService;

/** Security configuration for OAuth2 Authorization Code with PKCE and OIDC endpoints. */
@Configuration
@EnableMethodSecurity
public class AuthorizationServerConfiguration {
    /** Configures authorization-server and OIDC protocol endpoints. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(final HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults());
        return http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))).build();
    }

    /** Keeps bearer-only account APIs independent of interactive login sessions and CSRF tokens. */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(final HttpSecurity http,
            final ApiSecurityErrors errors, final JwtDecoder decoder,
            @Value("${app.oauth.issuer}") final String issuer,
            @Value("${app.oauth.web-client-id}") final String webClientId) throws Exception {
        return http.securityMatcher("/v1/**", "/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/users/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/accounts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/agents/*/contact").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.decoder(token -> {
                            final var credential = decoder.decode(token);
                            // ID tokens share the client audience but have no access-token scope claim.
                            // Keep this restriction on API authentication so OIDC logout can validate ID tokens.
                            if (!credential.hasClaim("scope")
                                    || !issuer.equals(credential.getClaimAsString("iss"))
                                    || credential.getAudience().stream().noneMatch(audience ->
                                            audience.equals(webClientId) || audience.equals("domain-mobile"))) {
                                throw new BadJwtException("An application access token is required");
                            }
                            return credential;
                        }).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /** Configures interactive login for the authorization endpoint. */
    @Bean
    @Order(3)
    public SecurityFilterChain applicationSecurityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .build();
    }

    /** Loads the login email while returning the immutable account UUID as JWT subject. */
    @Bean
    public UserDetailsService userDetailsService(final PlatformUserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(user -> User.withUsername(user.getId().toString())
                        .password(user.getPasswordHash())
                        .authorities(new SimpleGrantedAuthority(user.getRole().name()))
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    /** Uses BCrypt for password verification and protected client-secret storage. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Registers the web confidential client and its PKCE/refresh-token capabilities. */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            final JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /** Persists authorization-code and refresh-token state so it survives service restart and scaling. */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            final JdbcTemplate jdbcTemplate,
            final RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    /** Persists OIDC consent decisions per client and user. */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            final JdbcTemplate jdbcTemplate,
            final RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /** Seeds the web OAuth client once, without storing its secret in source control. */
    @Bean
    public CommandLineRunner initializeWebClient(
            final RegisteredClientRepository clients,
            final PasswordEncoder passwordEncoder,
            @Value("${app.oauth.web-client-id}") final String clientId,
            @Value("${app.oauth.web-client-secret}") final String clientSecret,
            @Value("${app.oauth.web-redirect-uri}") final String redirectUri) {
        return ignored -> {
            final RegisteredClient existing = clients.findByClientId(clientId);
            if (existing == null) {
                clients.save(buildWebClient(clientId, clientSecret, redirectUri, passwordEncoder));
            } else {
                // Add logout redirects from already trusted callback origins without rotating credentials.
                final var logoutUris = existing.getRedirectUris().stream()
                        .map(uri -> URI.create(uri).resolve("/login").toString()).toList();
                if (!existing.getPostLogoutRedirectUris().containsAll(logoutUris)) {
                    clients.save(RegisteredClient.from(existing)
                            .postLogoutRedirectUris(uris -> uris.addAll(logoutUris)).build());
                }
            }
        };
    }

    /** Registers a confidential mobile BFF client; no client secret is shipped to the native app. */
    @Bean
    public CommandLineRunner initializeMobileClient(final RegisteredClientRepository clients,final PasswordEncoder encoder,@Value("${OAUTH_MOBILE_CLIENT_SECRET:}") final String secret) {
        return ignored->{if(!secret.isBlank()&&clients.findByClientId("domain-mobile")==null)clients.save(RegisteredClient.withId(UUID.randomUUID().toString()).clientId("domain-mobile").clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("bemon://oauth").redirectUri("http://localhost:8082/oauth").scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
            .clientSettings(ClientSettings.builder().requireProofKey(true).build()).tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(15)).refreshTokenTimeToLive(Duration.ofDays(30)).reuseRefreshTokens(false).build()).build());};
    }

    private RegisteredClient buildWebClient(
            final String clientId,
            final String clientSecret,
            final String redirectUri,
            final PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(URI.create(redirectUri).resolve("/login").toString())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("listings:write")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    /** Retains the development signing key across restarts when a private persistent path is configured. */
    @Bean
    @Profile("dev")
    public JWKSource<SecurityContext> jwkSource(@Value("${DEV_SIGNING_KEY_PATH:}") final String path) throws Exception {
        if (!path.isBlank()) {
            final var file = java.nio.file.Path.of(path);
            if (java.nio.file.Files.exists(file)) return new ImmutableJWKSet<>(new JWKSet(RSAKey.parse(java.nio.file.Files.readString(file))));
            java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
            final KeyPair generated = generateRsaKey();
            final RSAKey key = new RSAKey.Builder((RSAPublicKey) generated.getPublic()).privateKey((RSAPrivateKey) generated.getPrivate()).keyID(UUID.randomUUID().toString()).build();
            java.nio.file.Files.writeString(file, key.toJSONString(), java.nio.file.StandardOpenOption.CREATE_NEW);
            java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            return new ImmutableJWKSet<>(new JWKSet(key));
        }
        final KeyPair keyPair = generateRsaKey();
        final RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /** Provides JWT decoding for OIDC UserInfo and resource-server support. */
    @Bean
    public JwtDecoder jwtDecoder(final JWKSource<SecurityContext> jwkSource, final AccountService accounts) {
        final JwtDecoder delegate = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        return value -> {
            final Jwt jwt = delegate.decode(value);
            final String epoch = jwt.getClaimAsString("auth_version");
            try {
                if (accounts.authVersion(UUID.fromString(jwt.getSubject())) != (epoch == null ? 0 : Long.parseLong(epoch)))
                    throw new org.springframework.security.oauth2.jwt.JwtException("Revoked account credentials");
            } catch (IllegalArgumentException error) { throw new org.springframework.security.oauth2.jwt.JwtException("Invalid account", error); }
            return jwt;
        };
    }

    /** Publishes issuer and endpoint metadata. */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${app.oauth.issuer}") final String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /** Adds application roles to access-token claims while standard scopes remain in the `scope` claim. */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(final AccountService accounts) {
        return context -> {
            // String claims survive Security's restrictive JDBC authorization JSON allowlist.
            context.getClaims().claim("auth_version", Long.toString(accounts.authVersion(UUID.fromString(context.getPrincipal().getName()))));
            context.getClaims().claim("roles", context.getPrincipal().getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                // ArrayList is supported by Spring Security's JDBC deserialization allowlist.
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
        };
    }

    /** Maps standard scopes and the custom JWT `roles` claim into Spring authorities. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            final Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
            final var roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
            }
            return authorities;
        });
        return authenticationConverter;
    }

    private KeyPair generateRsaKey() {
        try {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate RSA signing key", exception);
        }
    }
}
