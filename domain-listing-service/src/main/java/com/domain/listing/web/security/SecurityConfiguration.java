package com.domain.listing.web.security;

import com.domain.listing.web.error.ApiSecurityErrors;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/** JWT resource-server security configuration for gateway-facing requests. */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    /** Configures stateless bearer-token authentication and public health checks. */
    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
            final ApiSecurityErrors errors) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/properties/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /** Maps Identity's standard scopes and custom roles claim into Spring authorities. */
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
}
