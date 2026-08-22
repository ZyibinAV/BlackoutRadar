package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void validRegion() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");

        assertEquals("Омская область", region.name());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> Region.of(null, "Омская область"));
    }

    @Test
    void nullNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Region.of(UUID.randomUUID(), null));
    }

    @Test
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Region.of(UUID.randomUUID(), "   "));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region a = Region.of(id, "Омская область");
        Region b = Region.of(id, "Омская область");

        assertNotEquals(a, Region.of(UUID.randomUUID(), "Омская область"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}