package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for opaque refresh tokens.
 *
 * <p>The lifetime is security configuration, never a business domain rule.
 */
@ConfigurationProperties(prefix = "blackoutradar.security.refresh-token")
public class RefreshTokenProperties {

    private Duration lifetime = Duration.ofDays(30);

    public Duration getLifetime() {
        return lifetime;
    }

    public void setLifetime(Duration lifetime) {
        this.lifetime = lifetime;
    }
}
