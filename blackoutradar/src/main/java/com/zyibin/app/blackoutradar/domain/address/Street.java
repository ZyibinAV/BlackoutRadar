package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class Street {

    private final UUID id;
    private final City city;
    private final StreetType type;
    private final String canonicalName;

    private Street(UUID id, City city, StreetType type, String canonicalName) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.city = Objects.requireNonNull(city, "city must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.canonicalName = DomainPreconditions.requireNotBlank(canonicalName, "canonicalName must not be blank");
    }

    public static Street of(UUID id, City city, StreetType type, String canonicalName) {
        return new Street(id, city, type, canonicalName);
    }

    public UUID id() {
        return id;
    }

    public City city() {
        return city;
    }

    public StreetType type() {
        return type;
    }

    public String canonicalName() {
        return canonicalName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Street street)) {
            return false;
        }
        return id.equals(street.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Street{"
                + "id=" + id
                + ", city=" + city
                + ", type=" + type
                + ", canonicalName='" + canonicalName + '\''
                + '}';
    }
}