package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HouseTest {

    @Test
    void validValueObject() {
        House house = new House("15", null, "15");

        assertEquals("15", house.houseNumber());
        assertNull(house.houseAddition());
        assertEquals("15", house.canonicalHouse());
    }

    @Test
    void validWithAddition() {
        House house = new House("15", "А", "15а");

        assertEquals("А", house.houseAddition());
    }

    @Test
    void nullHouseNumberRejected() {
        assertThrows(NullPointerException.class, () -> new House(null, null, "15"));
    }

    @Test
    void blankHouseNumberRejected() {
        assertThrows(IllegalArgumentException.class, () -> new House("   ", null, "15"));
    }

    @Test
    void nullCanonicalHouseRejected() {
        assertThrows(NullPointerException.class, () -> new House("15", null, null));
    }

    @Test
    void blankCanonicalHouseRejected() {
        assertThrows(IllegalArgumentException.class, () -> new House("15", null, "  "));
    }

    @Test
    void equalityByValues() {
        House a = new House("15", "А", "15а");
        House b = new House("15", "А", "15а");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualForDifferentValues() {
        assertNotEquals(new House("15", "А", "15а"), new House("15", null, "15"));
        assertNotEquals(new House("15", "А", "15а"), new House("16", "А", "16а"));
    }
}