package com.zyibin.app.blackoutradar.domain.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransformerStationTest {

    @Test
    void validCreation() {
        TransformerStation station = TransformerStation.of(UUID.randomUUID(), "ТП-1");

        assertEquals("ТП-1", station.name());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> TransformerStation.of(null, "ТП-1"));
    }

    @Test
    void nullNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TransformerStation.of(UUID.randomUUID(), null));
    }

    @Test
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TransformerStation.of(UUID.randomUUID(), "   "));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        TransformerStation a = TransformerStation.of(id, "ТП-1");
        TransformerStation b = TransformerStation.of(id, "ТП-1");

        assertNotEquals(a, TransformerStation.of(UUID.randomUUID(), "ТП-1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}