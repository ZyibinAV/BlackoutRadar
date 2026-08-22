package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CityDistrictTest {

    @Test
    void validEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");

        assertEquals(city, district.city());
        assertEquals("Центральный", district.name());
    }

    @Test
    void nullCityRejected() {
        assertThrows(NullPointerException.class,
                () -> CityDistrict.of(UUID.randomUUID(), null, "Центральный"));
    }

    @Test
    void nullNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        assertThrows(IllegalArgumentException.class,
                () -> CityDistrict.of(UUID.randomUUID(), city, null));
    }

    @Test
    void blankNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        assertThrows(IllegalArgumentException.class,
                () -> CityDistrict.of(UUID.randomUUID(), city, "  "));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict a = CityDistrict.of(id, city, "Центральный");
        CityDistrict b = CityDistrict.of(id, city, "Центральный");

        assertNotEquals(a, CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}