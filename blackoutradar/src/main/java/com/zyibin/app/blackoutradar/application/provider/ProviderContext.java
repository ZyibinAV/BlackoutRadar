package com.zyibin.app.blackoutradar.application.provider;

import java.util.UUID;

public record ProviderContext(
        UUID sourceId,
        String configuration
) {
    public ProviderContext {
        if (sourceId == null) {
            throw new NullPointerException("sourceId must not be null");
        }
        if (configuration != null && configuration.isBlank()) {
            throw new IllegalArgumentException("configuration must not be blank");
        }
    }
}
