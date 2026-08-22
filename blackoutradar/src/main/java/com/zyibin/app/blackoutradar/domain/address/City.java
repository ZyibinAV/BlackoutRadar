package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class City {

    private final UUID id;
    private final Region region;
    private final RegionalDistrict regionalDistrict;
    private final String name;

    private City(UUID id, Region region, RegionalDistrict regionalDistrict, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.region = Objects.requireNonNull(region, "region must not be null");
        this.regionalDistrict = validateRegionalDistrict(region, regionalDistrict);
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
    }

    public static City of(UUID id, Region region, String name) {
        return new City(id, region, null, name);
    }

    public static City of(UUID id, Region region, RegionalDistrict regionalDistrict, String name) {
        return new City(id, region, regionalDistrict, name);
    }

    private static RegionalDistrict validateRegionalDistrict(Region region, RegionalDistrict regionalDistrict) {
        if (regionalDistrict == null) {
            return null;
        }
        if (!Objects.equals(region.id(), regionalDistrict.region().id())) {
            throw new IllegalArgumentException("regionalDistrict must belong to the same region as city");
        }
        return regionalDistrict;
    }

    public UUID id() {
        return id;
    }

    public Region region() {
        return region;
    }

    public RegionalDistrict regionalDistrict() {
        return regionalDistrict;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof City city)) {
            return false;
        }
        return id.equals(city.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "City{"
                + "id=" + id
                + ", region=" + region
                + ", regionalDistrict=" + regionalDistrict
                + ", name='" + name + '\''
                + '}';
    }
}