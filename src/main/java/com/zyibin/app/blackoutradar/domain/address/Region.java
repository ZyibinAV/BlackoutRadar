package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class Region {

    private final UUID id;
    private final String name;

    private Region(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
    }

    public static Region of(UUID id, String name) {
        return new Region(id, name);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Region region)) {
            return false;
        }
        return id.equals(region.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Region{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
