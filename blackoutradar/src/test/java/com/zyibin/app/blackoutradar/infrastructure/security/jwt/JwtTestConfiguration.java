package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class JwtTestConfiguration {

    @Bean
    @Primary
    public JwtProperties jwtTestProperties() {
        KeyPair keyPair = generateKeyPair();

        JwtProperties properties = new JwtProperties();
        properties.setIssuer("blackoutradar-test");
        properties.setAudience("blackoutradar-test-api");
        properties.setAccessTokenLifetime(java.time.Duration.ofMinutes(15));
        properties.setPrivateKey(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        properties.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));

        return properties;
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA algorithm is not available", exception);
        }
    }

    private String toPem(String type, byte[] encodedKey) {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encodedKey);

        return "-----BEGIN " + type + "-----\n"
                + encoded
                + "\n-----END " + type + "-----";
    }
}
