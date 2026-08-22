package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CityTest {

    @Test
    void validCityWithoutRegionalDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");

        assertEquals(region, city.region());
        assertNull(city.regionalDistrict());
        assertEquals("Омск", city.name());
    }

    @Test
    void validCityWithRegionalDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");
        City city = City.of(UUID.randomUUID(), region, district, "Лузино");

        assertSame(district, city.regionalDistrict());
        assertEquals(region, city.region());
    }

    @Test
    void nullRegionRejected() {
        assertThrows(NullPointerException.class,
                () -> City.of(UUID.randomUUID(), null, "Омск"));
    }

    @Test
    void nullNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        assertThrows(IllegalArgumentException.class,
                () -> City.of(UUID.randomUUID(), region, null));
    }

    @Test
    void blankNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        assertThrows(IllegalArgumentException.class,
                () -> City.of(UUID.randomUUID(), region, "   "));
    }

    @Test
    void regionalDistrictFromAnotherRegionRejected() {
        Region regionA = Region.of(UUID.randomUUID(), "Регион А");
        Region regionB = Region.of(UUID.randomUUID(), "Регион Б");
        RegionalDistrict districtB = RegionalDistrict.of(
                UUID.randomUUID(), regionB, RegionalDistrictType.MUNICIPAL_DISTRICT, "Район Б");

        assertThrows(IllegalArgumentException.class,
                () -> City.of(UUID.randomUUID(), regionA, districtB, "Город"));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City a = City.of(id, region, "Омск");
        City b = City.of(id, region, "Омск");

        assertNotEquals(a, City.of(UUID.randomUUID(), region, "Омск"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}