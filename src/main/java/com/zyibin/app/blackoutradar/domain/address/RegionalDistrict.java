package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class RegionalDistrict {

    private final UUID id;
    private final Region region;
    private final RegionalDistrictType type;
    private final String name;

    private RegionalDistrict(UUID id, Region region, RegionalDistrictType type, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.region = Objects.requireNonNull(region, "region must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
    }

    public static RegionalDistrict of(UUID id, Region region, RegionalDistrictType type, String name) {
        return new RegionalDistrict(id, region, type, name);
    }

    public UUID id() {
        return id;
    }

    public Region region() {
        return region;
    }

    public RegionalDistrictType type() {
        return type;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegionalDistrict that)) {
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
        return "RegionalDistrict{"
                + "id=" + id
                + ", region=" + region
                + ", type=" + type
                + ", name='" + name + '\''
                + '}';
    }
}