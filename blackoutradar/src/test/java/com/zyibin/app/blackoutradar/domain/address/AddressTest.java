package com.zyibin.app.blackoutradar.domain.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddressTest {

    @Test
    void validAddressWithoutCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");
        Address address = Address.of(UUID.randomUUID(), street, house);

        assertEquals(street, address.street());
        assertEquals(house, address.house());
        assertNull(address.cityDistrict());
    }

    @Test
    void validAddressWithCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");
        Address address = Address.of(UUID.randomUUID(), street, district, house);

        assertSame(district, address.cityDistrict());
        assertEquals(city, address.street().city());
    }

    @Test
    void cityDistrictFromAnotherCityRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City cityA = City.of(UUID.randomUUID(), region, "Омск");
        City cityB = City.of(UUID.randomUUID(), region, "Тара");
        CityDistrict districtB = CityDistrict.of(UUID.randomUUID(), cityB, "Центральный");
        Street streetA = Street.of(UUID.randomUUID(), cityA, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");

        assertThrows(IllegalArgumentException.class,
                () -> Address.of(UUID.randomUUID(), streetA, districtB, house));
    }

    @Test
    void nullStreetRejected() {
        House house = new House("15", null, "15");
        assertThrows(NullPointerException.class,
                () -> Address.of(UUID.randomUUID(), null, house));
    }

    @Test
    void nullHouseRejected() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        assertThrows(NullPointerException.class,
                () -> Address.of(UUID.randomUUID(), street, null));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");
        Address a = Address.of(id, street, house);
        Address b = Address.of(id, street, house);

        assertNotEquals(a, Address.of(UUID.randomUUID(), street, house));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}