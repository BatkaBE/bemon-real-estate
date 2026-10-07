package com.domain.payment;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
/** Enforces verified account epochs, audiences and role-based purchase/admin boundaries. */
@Configuration @EnableMethodSecurity
public class PaymentSecurity {
    /** Configures stateless JWT authentication and callback hints guarded by their own opaque secret. */
    @Bean
    public SecurityFilterChain chain(final HttpSecurity http) throws Exception {
        final var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(jwt->{
            final var roles=jwt.getClaimAsStringList("roles");return roles==null?List.of():roles.stream().map(role->(org.springframework.security.core.GrantedAuthority)new SimpleGrantedAuthority(role)).toList();});
        return http.csrf(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/v1/payments/callback/**","/v1/payments/offers").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).build();
    }
    /** Queries revocation through Identity rather than sharing its database. */
    @Bean
    public JwtDecoder decoder(final Transport http,@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") final String keys,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuer,@Value("${app.identity-url}") final String identity,@Value("${app.internal-key}") final String key){
        final var decoder=NimbusJwtDecoder.withJwkSetUri(keys).build();
        final OAuth2TokenValidator<Jwt> account=jwt->{try{
            if(!jwt.hasClaim("scope")||jwt.getAudience().stream().noneMatch(a->List.of("domain-web","domain-mobile").contains(a)))throw new IllegalArgumentException();
            final var version=http.send(identity+"/internal/accounts/"+UUID.fromString(jwt.getSubject())+"/version","GET",null,"X-Internal-Key",key).get("version");
            final String epoch=jwt.getClaimAsString("auth_version");if(((Number)version).longValue()!=(epoch==null?0:Long.parseLong(epoch)))throw new IllegalArgumentException();
            return OAuth2TokenValidatorResult.success();
        }catch(Exception unavailable){return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));}};
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),account));return decoder;
    }
}
