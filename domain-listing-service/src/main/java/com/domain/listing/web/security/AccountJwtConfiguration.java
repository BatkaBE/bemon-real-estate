package com.domain.listing.web.security;

import com.domain.listing.application.IdentityBridge;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

/** Adds immediate account revocation and explicit client audiences to cryptographic JWT checks. */
@Configuration
@ConditionalOnProperty(name="app.account-revocation.enabled",havingValue="true")
public class AccountJwtConfiguration {
    /** Fails closed if the trusted Identity service cannot confirm this credential epoch. */
    @Bean
    public JwtDecoder accountJwtDecoder(final IdentityBridge identity,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") final String keys,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuer) {
        final NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(keys).build();
        final OAuth2TokenValidator<Jwt> account=jwt->{
            try {
                final String epoch=jwt.getClaimAsString("auth_version");
                if(!jwt.hasClaim("scope")
                        ||jwt.getAudience().stream().noneMatch(a->a.equals("domain-web")||a.equals("domain-mobile"))
                        ||identity.authVersion(UUID.fromString(jwt.getSubject()))!=(epoch==null?0:Long.parseLong(epoch)))
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                return OAuth2TokenValidatorResult.success();
            }catch(Exception unavailable){return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));}
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),account));return decoder;
    }
}
