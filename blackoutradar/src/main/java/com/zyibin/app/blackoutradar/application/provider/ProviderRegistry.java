package com.zyibin.app.blackoutradar.application.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {

    private final Map<String, OutageProvider> index;

    public ProviderRegistry(List<OutageProvider> providers) {
        Objects.requireNonNull(providers, "providers must not be null");
        Map<String, OutageProvider> map = new HashMap<>();
        for (OutageProvider provider : providers) {
            Objects.requireNonNull(provider, "provider must not be null");
            String type = provider.providerType();
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("providerType must not be blank");
            }
            if (map.containsKey(type)) {
                throw new IllegalStateException("Duplicate providerType: " + type);
            }
            map.put(type, provider);
        }
        this.index = Map.copyOf(map);
    }

    public Optional<OutageProvider> find(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            throw new IllegalArgumentException("providerType must not be blank");
        }
        return Optional.ofNullable(index.get(providerType));
    }
}
