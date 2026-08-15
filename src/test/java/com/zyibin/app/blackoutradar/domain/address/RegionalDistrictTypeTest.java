package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class RegionalDistrictTypeTest {

    @Test
    void allApprovedValuesExist() {
        assertEquals(EnumSet.of(
                RegionalDistrictType.MUNICIPAL_DISTRICT,
                RegionalDistrictType.MUNICIPAL_OKRUG,
                RegionalDistrictType.URBAN_OKRUG,
                RegionalDistrictType.INTRACITY_TERRITORY,
                RegionalDistrictType.FEDERAL_TERRITORY), EnumSet.allOf(RegionalDistrictType.class));
    }

    @Test
    void englishIdentifiers() {
        for (RegionalDistrictType type : RegionalDistrictType.values()) {
            assertTrue(type.name().matches("[A-Z_]+"));
        }
    }
}