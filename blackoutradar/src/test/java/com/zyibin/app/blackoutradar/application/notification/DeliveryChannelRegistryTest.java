package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryChannelRegistryTest {

    static class StubAdapter implements DeliveryPort {
        private final String type;
        StubAdapter(String type) { this.type = type; }
        @Override public String channelType() { return type; }
        @Override public DeliveryResult deliver(NotificationChannel channel, String message) {
            return DeliveryResult.success();
        }
    }

    @Test
    void findsAdapterByType() {
        DeliveryPort email = new StubAdapter("email");
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of(email));
        Optional<DeliveryPort> found = registry.find("email");
        assertTrue(found.isPresent());
        assertEquals(email, found.get());
    }

    @Test
    void unknownTypeReturnsEmpty() {
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of(new StubAdapter("email")));
        assertTrue(registry.find("sms").isEmpty());
    }

    @Test
    void duplicateChannelTypeThrowsOnConstruction() {
        var ex = assertThrows(IllegalStateException.class,
                () -> new DeliveryChannelRegistry(List.of(new StubAdapter("dup"), new StubAdapter("dup"))));
        assertTrue(ex.getMessage().contains("dup"));
    }

    @Test
    void emptyRegistryFindReturnsEmpty() {
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of());
        assertTrue(registry.find("any").isEmpty());
    }

    @Test
    void blankChannelTypeRejected() {
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of(new StubAdapter("email")));
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> registry.find("   "));
    }

    @Test
    void nullAdaptersRejected() {
        assertThrows(NullPointerException.class, () -> new DeliveryChannelRegistry(null));
    }

    @Test
    void doesNotExposeListOrRegister() {
        for (var method : DeliveryChannelRegistry.class.getDeclaredMethods()) {
            assertTrue(!method.getName().equals("list"), "Registry must not have list()");
            assertTrue(!method.getName().equals("register"), "Registry must not have register()");
        }
    }

    @Test
    void newChannelTypeResolvesWithoutDomainChange() {
        DeliveryPort future = new StubAdapter("future-channel");
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of(future));
        assertTrue(registry.find("future-channel").isPresent());
    }
}
