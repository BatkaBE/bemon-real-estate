package com.domain.identity.signing;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Loads a stable RSA signing key from an injected PKCS#12 keystore outside development. */
@Configuration
@Profile("!dev")
public class KeystoreSigningKeyConfiguration {
    /** Creates the JWK source used to publish public keys and sign access tokens. */
    @Bean
    public JWKSource<SecurityContext> jwkSource(final SigningKeyProperties properties) {
        try (InputStream input = properties.keystoreLocation().getInputStream()) {
            final KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, properties.keystorePassword().toCharArray());
            final Key key = keyStore.getKey(properties.keyAlias(), properties.keyPassword().toCharArray());
            final Certificate certificate = keyStore.getCertificate(properties.keyAlias());
            if (certificate == null || !(key instanceof PrivateKey privateKey)
                    || !(privateKey instanceof RSAPrivateKey rsaPrivateKey)
                    || !(certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalStateException("Signing keystore alias must contain an RSA private key and certificate");
            }
            final RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey)
                    .privateKey(rsaPrivateKey)
                    .keyID(properties.keyId())
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the configured JWT signing key", exception);
        }
    }
}
