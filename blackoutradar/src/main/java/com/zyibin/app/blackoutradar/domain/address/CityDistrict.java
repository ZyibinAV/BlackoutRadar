package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class CityDistrict {

    private final UUID id;
    private final City city;
    private final String name;

    private CityDistrict(UUID id, City city, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.city = Objects.requireNonNull(city, "city must not be null");
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
    }

    public static CityDistrict of(UUID id, City city, String name) {
        return new CityDistrict(id, city, name);
    }

    public UUID id() {
        return id;
    }

    public City city() {
        return city;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CityDistrict that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CityDistrict{"
                + "id=" + id
                + ", city=" + city
                + ", name='" + name + '\''
                + '}';
    }
}