package com.zyibin.app.blackoutradar.domain.outage;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class Source {

    private final UUID id;
    private final String name;
    private final String sourceType;
    private final String providerType;
    private final String configuration;
    private final String schedule;
    private final boolean isActive;

    private Source(UUID id, String name, String sourceType, String providerType,
                   String configuration, String schedule, boolean isActive) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = DomainPreconditions.requireNotBlank(name, "name must not be blank");
        this.sourceType = DomainPreconditions.requireNotBlank(sourceType, "sourceType must not be blank");
        this.providerType = DomainPreconditions.requireNotBlank(providerType, "providerType must not be blank");
        this.configuration = configuration;
        this.schedule = DomainPreconditions.requireNotBlank(schedule, "schedule must not be blank");
        this.isActive = isActive;
    }

    public static Source of(UUID id, String name, String sourceType, String providerType,
                            String schedule, boolean isActive) {
        return new Source(id, name, sourceType, providerType, null, schedule, isActive);
    }

    public static Source of(UUID id, String name, String sourceType, String providerType,
                            String configuration, String schedule, boolean isActive) {
        return new Source(id, name, sourceType, providerType, configuration, schedule, isActive);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String sourceType() {
        return sourceType;
    }

    public String providerType() {
        return providerType;
    }

    public String configuration() {
        return configuration;
    }

    public String schedule() {
        return schedule;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Source source)) {
            return false;
        }
        return id.equals(source.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Source{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", sourceType='" + sourceType + '\''
                + ", providerType='" + providerType + '\''
                + ", configuration='" + configuration + '\''
                + ", schedule='" + schedule + '\''
                + ", isActive=" + isActive
                + '}';
    }
}