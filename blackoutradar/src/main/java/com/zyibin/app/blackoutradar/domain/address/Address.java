package com.zyibin.app.blackoutradar.domain.address;

import java.util.Objects;
import java.util.UUID;

public final class Address {

    private final UUID id;
    private final Street street;
    private final CityDistrict cityDistrict;
    private final House house;

    private Address(UUID id, Street street, CityDistrict cityDistrict, House house) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.street = Objects.requireNonNull(street, "street must not be null");
        this.house = Objects.requireNonNull(house, "house must not be null");
        this.cityDistrict = validateCityDistrict(street, cityDistrict);
    }

    public static Address of(UUID id, Street street, House house) {
        return new Address(id, street, null, house);
    }

    public static Address of(UUID id, Street street, CityDistrict cityDistrict, House house) {
        return new Address(id, street, cityDistrict, house);
    }

    private static CityDistrict validateCityDistrict(Street street, CityDistrict cityDistrict) {
        if (cityDistrict == null) {
            return null;
        }
        if (!Objects.equals(street.city().id(), cityDistrict.city().id())) {
            throw new IllegalArgumentException("cityDistrict must belong to the same city as street");
        }
        return cityDistrict;
    }

    public UUID id() {
        return id;
    }

    public Street street() {
        return street;
    }

    public CityDistrict cityDistrict() {
        return cityDistrict;
    }

    public House house() {
        return house;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Address address)) {
            return false;
        }
        return id.equals(address.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Address{"
                + "id=" + id
                + ", street=" + street
                + ", cityDistrict=" + cityDistrict
                + ", house=" + house
                + '}';
    }
}