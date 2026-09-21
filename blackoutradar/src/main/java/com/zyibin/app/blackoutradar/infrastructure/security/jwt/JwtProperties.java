package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for JWT access tokens.
 *
 * <p>All lifetimes are configuration, never business domain constants.
 * Signing keys are PEM-encoded RSA keys provided via environment and never
 * stored in source code. Keys are parsed lazily on first token operation so
 * that application context startup never fails because of missing secrets.
 */
@ConfigurationProperties(prefix = "blackoutradar.security.jwt")
public class JwtProperties {

    private String issuer = "blackoutradar";
    private String audience = "blackoutradar-api";
    private Duration accessTokenLifetime = Duration.ofMinutes(15);
    private String privateKey;
    private String publicKey;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenLifetime() {
        return accessTokenLifetime;
    }

    public void setAccessTokenLifetime(Duration accessTokenLifetime) {
        this.accessTokenLifetime = accessTokenLifetime;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
