package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ParsedOutage(
        Source source,
        Instant startTime,
        Instant endTime,
        String reason,
        String externalReference,
        List<AddressInput> addresses
) {
    public ParsedOutage {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        Objects.requireNonNull(addresses, "addresses must not be null");
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("addresses must not be empty");
        }
        for (AddressInput addr : addresses) {
            Objects.requireNonNull(addr, "address must not be null");
        }
        addresses = List.copyOf(addresses);
        if (externalReference != null && externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference must not be blank");
        }
    }
}
