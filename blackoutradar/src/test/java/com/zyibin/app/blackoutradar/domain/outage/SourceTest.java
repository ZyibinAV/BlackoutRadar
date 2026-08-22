package com.zyibin.app.blackoutradar.domain.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceTest {

    @Test
    void validSource() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        assertEquals("Горэлектросеть", source.name());
        assertEquals("ТЕЛЕГРАМ", source.sourceType());
        assertEquals("Официальный", source.providerType());
        assertEquals("0 6 * * *", source.schedule());
        assertEquals(true, source.isActive());
    }

    @Test
    void validSourceWithConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "config", "0 6 * * *", false);

        assertEquals("config", source.configuration());
        assertEquals(false, source.isActive());
    }

    @Test
    void validSourceWithoutConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        assertEquals(null, source.configuration());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> Source.of(null, "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
    }

    @Test
    void nullNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), null, "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
    }

    @Test
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "   ", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
    }

    @Test
    void nullSourceTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "Горэлектросеть", null, "Официальный",
                        "0 6 * * *", true));
    }

    @Test
    void blankSourceTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "Горэлектросеть", "  ", "Официальный",
                        "0 6 * * *", true));
    }

    @Test
    void nullProviderTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", null,
                        "0 6 * * *", true));
    }

    @Test
    void nullScheduleRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                        null, true));
    }

    @Test
    void blankScheduleRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                        "   ", true));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Source a = Source.of(id, "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Source b = Source.of(id, "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);

        assertNotEquals(a, Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ",
                "Официальный", "0 6 * * *", true));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}