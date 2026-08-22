package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegionalDistrictTest {

    @Test
    void validEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");

        assertEquals(region, district.region());
        assertEquals(RegionalDistrictType.MUNICIPAL_DISTRICT, district.type());
        assertEquals("Омский район", district.name());
    }

    @Test
    void nullRegionRejected() {
        assertThrows(NullPointerException.class,
                () -> RegionalDistrict.of(UUID.randomUUID(), null,
                        RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));
    }

    @Test
    void nullTypeRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        assertThrows(NullPointerException.class,
                () -> RegionalDistrict.of(UUID.randomUUID(), region, null, "Омский район"));
    }

    @Test
    void nullNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        assertThrows(IllegalArgumentException.class,
                () -> RegionalDistrict.of(UUID.randomUUID(), region,
                        RegionalDistrictType.MUNICIPAL_DISTRICT, null));
    }

    @Test
    void blankNameRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        assertThrows(IllegalArgumentException.class,
                () -> RegionalDistrict.of(UUID.randomUUID(), region,
                        RegionalDistrictType.MUNICIPAL_DISTRICT, "  "));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict a = RegionalDistrict.of(id, region, RegionalDistrictType.URBAN_OKRUG, "Омск");
        RegionalDistrict b = RegionalDistrict.of(id, region, RegionalDistrictType.URBAN_OKRUG, "Омск");

        assertNotEquals(a, RegionalDistrict.of(UUID.randomUUID(), region, RegionalDistrictType.URBAN_OKRUG, "Омск"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}