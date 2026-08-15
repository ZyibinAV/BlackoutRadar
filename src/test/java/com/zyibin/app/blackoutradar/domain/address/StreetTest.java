package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StreetTest {

    @Test
    void validEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");

        assertEquals(city, street.city());
        assertEquals(StreetType.STREET, street.type());
        assertEquals("Ленина", street.canonicalName());
    }

    @Test
    void nullCityRejected() {
        assertThrows(NullPointerException.class,
                () -> Street.of(UUID.randomUUID(), null, StreetType.STREET, "Ленина"));
    }

    @Test
    void nullStreetTypeRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        assertThrows(NullPointerException.class,
                () -> Street.of(UUID.randomUUID(), city, null, "Ленина"));
    }

    @Test
    void nullCanonicalNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        assertThrows(IllegalArgumentException.class,
                () -> Street.of(UUID.randomUUID(), city, StreetType.STREET, null));
    }

    @Test
    void blankCanonicalNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        assertThrows(IllegalArgumentException.class,
                () -> Street.of(UUID.randomUUID(), city, StreetType.STREET, " "));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street a = Street.of(id, city, StreetType.STREET, "Ленина");
        Street b = Street.of(id, city, StreetType.STREET, "Ленина");

        assertNotEquals(a, Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}