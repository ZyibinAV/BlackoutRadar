package com.zyibin.app.blackoutradar.domain.subscription;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class TransformerStation {

    private final UUID id;
    private final String name;

    private TransformerStation(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
    }

    public static TransformerStation of(UUID id, String name) {
        return new TransformerStation(id, name);
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
        if (!(o instanceof TransformerStation that)) {
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
        return "TransformerStation{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}