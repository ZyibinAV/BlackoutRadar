package com.zyibin.app.blackoutradar.domain.outage;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PowerOutage {

    private final UUID id;
    private final Source source;
    private final Instant startTime;
    private final Instant endTime;
    private final String reason;
    private final String status;
    private final Set<PowerOutageAddress> addresses;

    private PowerOutage(UUID id, Source source, Instant startTime, Instant endTime,
                        String reason, String status, Collection<PowerOutageAddress> addresses) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        this.reason = DomainPreconditions.requireNotBlank(reason, "reason must not be blank");
        this.status = DomainPreconditions.requireNotBlank(status, "status must not be blank");
        this.addresses = copyAddresses(addresses);
    }

    public static PowerOutage of(UUID id, Source source, Instant startTime, Instant endTime,
                                 String reason, String status, Collection<PowerOutageAddress> addresses) {
        return new PowerOutage(id, source, startTime, endTime, reason, status, addresses);
    }

    private Set<PowerOutageAddress> copyAddresses(Collection<PowerOutageAddress> addresses) {
        Objects.requireNonNull(addresses, "addresses must not be null");
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("addresses must not be empty");
        }
        Set<UUID> seen = new HashSet<>();
        Set<PowerOutageAddress> result = new LinkedHashSet<>();
        for (PowerOutageAddress address : addresses) {
            Objects.requireNonNull(address, "address must not be null");
            UUID addressId = address.address().id();
            if (!seen.add(addressId)) {
                throw new IllegalArgumentException("duplicate address in power outage: " + addressId);
            }
            result.add(address.withPowerOutage(this));
        }
        return Collections.unmodifiableSet(result);
    }

    public UUID id() {
        return id;
    }

    public Source source() {
        return source;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public String reason() {
        return reason;
    }

    public String status() {
        return status;
    }

    public Set<PowerOutageAddress> addresses() {
        return addresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PowerOutage that)) {
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
        return "PowerOutage{"
                + "id=" + id
                + ", source=" + source
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", reason='" + reason + '\''
                + ", status='" + status + '\''
                + ", addresses=" + addresses
                + '}';
    }
}