package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreetTypeTest {

    private static final Set<StreetType> APPROVED_VALUES = EnumSet.of(
            StreetType.STREET,
            StreetType.PROSPECT,
            StreetType.BOULEVARD,
            StreetType.LANE,
            StreetType.PASSAGE,
            StreetType.SQUARE,
            StreetType.EMBANKMENT,
            StreetType.HIGHWAY,
            StreetType.ROAD,
            StreetType.TRACT,
            StreetType.ALLEY,
            StreetType.DEAD_END,
            StreetType.DESCENT,
            StreetType.MAGISTRAL,
            StreetType.UNKNOWN);

    @Test
    void allApprovedValuesExist() {
        assertEquals(APPROVED_VALUES, EnumSet.allOf(StreetType.class));
    }

    @Test
    void unknownExists() {
        assertTrue(APPROVED_VALUES.contains(StreetType.UNKNOWN));
    }

    @Test
    void unknownDoesNotHaveWildcardSemantics() {
        StreetType unknown = StreetType.UNKNOWN;

        for (StreetType other : StreetType.values()) {
            if (other == StreetType.UNKNOWN) {
                continue;
            }
            assertFalse(unknown == other, "UNKNOWN must not equal " + other);
        }

        assertSame(StreetType.UNKNOWN, StreetType.valueOf("UNKNOWN"));
    }
}