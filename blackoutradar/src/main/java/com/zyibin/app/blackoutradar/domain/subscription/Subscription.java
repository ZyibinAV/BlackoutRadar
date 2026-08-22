package com.zyibin.app.blackoutradar.domain.subscription;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.identity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Subscription {

    private final UUID id;
    private final User user;
    private final Address address;
    private final Instant monitoringStart;
    private final Instant monitoringEnd;
    private final boolean isActive;
    private final Instant serviceAccessUntil;
    private final Set<TransformerStation> transformerStations;

    private Subscription(UUID id, User user, Address address,
                         Instant monitoringStart, Instant monitoringEnd,
                         boolean isActive, Instant serviceAccessUntil,
                         Collection<TransformerStation> transformerStations) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.monitoringStart = Objects.requireNonNull(monitoringStart, "monitoringStart must not be null");
        this.monitoringEnd = Objects.requireNonNull(monitoringEnd, "monitoringEnd must not be null");
        if (!monitoringStart.isBefore(monitoringEnd)) {
            throw new IllegalArgumentException("monitoringStart must be before monitoringEnd");
        }
        this.isActive = isActive;
        this.serviceAccessUntil = Objects.requireNonNull(serviceAccessUntil, "serviceAccessUntil must not be null");
        this.transformerStations = copyTransformerStations(transformerStations);
    }

    public static Subscription of(UUID id, User user, Address address,
                                  Instant monitoringStart, Instant monitoringEnd,
                                  boolean isActive, Instant serviceAccessUntil) {
        return new Subscription(id, user, address, monitoringStart, monitoringEnd,
                isActive, serviceAccessUntil, Collections.emptyList());
    }

    public static Subscription of(UUID id, User user, Address address,
                                  Instant monitoringStart, Instant monitoringEnd,
                                  boolean isActive, Instant serviceAccessUntil,
                                  Collection<TransformerStation> transformerStations) {
        return new Subscription(id, user, address, monitoringStart, monitoringEnd,
                isActive, serviceAccessUntil, transformerStations);
    }

    private static Set<TransformerStation> copyTransformerStations(
            Collection<TransformerStation> transformerStations) {
        if (transformerStations == null) {
            throw new IllegalArgumentException("transformerStations must not be null");
        }
        Set<TransformerStation> copy = new LinkedHashSet<>();
        for (TransformerStation station : transformerStations) {
            Objects.requireNonNull(station, "transformerStation must not be null");
            if (!copy.add(station)) {
                throw new IllegalArgumentException("duplicate transformerStation: " + station.id());
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    public UUID id() {
        return id;
    }

    public User user() {
        return user;
    }

    public Address address() {
        return address;
    }

    public Instant monitoringStart() {
        return monitoringStart;
    }

    public Instant monitoringEnd() {
        return monitoringEnd;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant serviceAccessUntil() {
        return serviceAccessUntil;
    }

    public Set<TransformerStation> transformerStations() {
        return transformerStations;
    }

    public boolean hasTransformerStations() {
        return !transformerStations.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Subscription that)) {
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
        return "Subscription{"
                + "id=" + id
                + ", user=" + user
                + ", address=" + address
                + ", monitoringStart=" + monitoringStart
                + ", monitoringEnd=" + monitoringEnd
                + ", isActive=" + isActive
                + ", serviceAccessUntil=" + serviceAccessUntil
                + ", transformerStations=" + new ArrayList<>(transformerStations)
                + '}';
    }
}