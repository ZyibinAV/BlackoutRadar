package com.zyibin.app.blackoutradar.application.notification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeliveryChannelRegistry {

    private final Map<String, DeliveryPort> index;

    public DeliveryChannelRegistry(List<DeliveryPort> adapters) {
        Objects.requireNonNull(adapters, "adapters must not be null");
        Map<String, DeliveryPort> map = new HashMap<>();
        for (DeliveryPort adapter : adapters) {
            Objects.requireNonNull(adapter, "adapter must not be null");
            String type = adapter.channelType();
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("channelType must not be blank");
            }
            if (map.containsKey(type)) {
                throw new IllegalStateException("Duplicate channelType: " + type);
            }
            map.put(type, adapter);
        }
        this.index = Map.copyOf(map);
    }

    public Optional<DeliveryPort> find(String channelType) {
        if (channelType == null || channelType.isBlank()) {
            throw new IllegalArgumentException("channelType must not be blank");
        }
        return Optional.ofNullable(index.get(channelType));
    }
}
