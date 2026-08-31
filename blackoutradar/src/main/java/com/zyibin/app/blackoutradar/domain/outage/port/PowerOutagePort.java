package com.zyibin.app.blackoutradar.domain.outage.port;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PowerOutagePort {

    Optional<PowerOutage> findById(UUID id);

    PowerOutage save(PowerOutage powerOutage);

    Optional<PowerOutage> findBySourceAndExternalReference(UUID sourceId, String externalReference);

    Optional<PowerOutage> findBySourceAndFallbackIdentity(UUID sourceId, Instant startTime, Set<UUID> canonicalAddressIds);

    record CreateResult(boolean created, PowerOutage powerOutage) {}

    CreateResult tryCreateWithExternalReference(PowerOutage powerOutage, String externalReference);

    CreateResult tryCreateWithFallback(PowerOutage powerOutage);
}