package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DuplicateResolver {

    private final PowerOutagePort powerOutagePort;

    public DuplicateResolver(PowerOutagePort powerOutagePort) {
        this.powerOutagePort = Objects.requireNonNull(powerOutagePort);
    }

    @Transactional
    public ResolutionResult resolve(ParsedOutage parsedOutage, List<Address> canonicalAddresses) {
        Objects.requireNonNull(parsedOutage, "parsedOutage must not be null");
        Objects.requireNonNull(canonicalAddresses, "canonicalAddresses must not be null");
        if (canonicalAddresses.isEmpty()) {
            throw new IllegalArgumentException("canonicalAddresses must not be empty");
        }

        String externalRef = parsedOutage.externalReference();
        if (externalRef != null && !externalRef.isBlank()) {
            return resolveByExternalReference(parsedOutage, canonicalAddresses, externalRef.trim());
        }
        return resolveByFallback(parsedOutage, canonicalAddresses);
    }

    private ResolutionResult resolveByExternalReference(ParsedOutage parsedOutage, List<Address> canonicalAddresses, String externalReference) {
        var existingOpt = powerOutagePort.findBySourceAndExternalReference(parsedOutage.source().id(), externalReference);
        if (existingOpt.isEmpty()) {
            PowerOutage created = createPowerOutage(parsedOutage, canonicalAddresses);
            var result = powerOutagePort.tryCreateWithExternalReference(created, externalReference);
            if (result.created()) {
                return new ResolutionResult(Decision.CREATE, result.powerOutage());
            }
            PowerOutage existing = result.powerOutage();
            if (isSameData(existing, parsedOutage, canonicalAddresses)) {
                return new ResolutionResult(Decision.IGNORE, existing);
            }
            PowerOutage updated = updatePowerOutage(existing, parsedOutage, canonicalAddresses);
            PowerOutage saved = powerOutagePort.save(updated);
            return new ResolutionResult(Decision.UPDATE, saved);
        }
        PowerOutage existing = existingOpt.get();
        if (isSameData(existing, parsedOutage, canonicalAddresses)) {
            return new ResolutionResult(Decision.IGNORE, existing);
        }
        PowerOutage updated = updatePowerOutage(existing, parsedOutage, canonicalAddresses);
        PowerOutage saved = powerOutagePort.save(updated);
        return new ResolutionResult(Decision.UPDATE, saved);
    }

    private ResolutionResult resolveByFallback(ParsedOutage parsedOutage, List<Address> canonicalAddresses) {
        Set<UUID> addressIds = canonicalAddresses.stream().map(Address::id).collect(Collectors.toSet());
        var existingOpt = powerOutagePort.findBySourceAndFallbackIdentity(parsedOutage.source().id(), parsedOutage.startTime(), addressIds);
        if (existingOpt.isEmpty()) {
            PowerOutage created = createPowerOutage(parsedOutage, canonicalAddresses);
            var result = powerOutagePort.tryCreateWithFallback(created);
            if (result.created()) {
                return new ResolutionResult(Decision.CREATE, result.powerOutage());
            }
            PowerOutage existing = result.powerOutage();
            if (isSameData(existing, parsedOutage, canonicalAddresses)) {
                return new ResolutionResult(Decision.IGNORE, existing);
            }
            PowerOutage updated = updatePowerOutage(existing, parsedOutage, canonicalAddresses);
            PowerOutage saved = powerOutagePort.save(updated);
            return new ResolutionResult(Decision.UPDATE, saved);
        }
        PowerOutage existing = existingOpt.get();
        if (isSameData(existing, parsedOutage, canonicalAddresses)) {
            return new ResolutionResult(Decision.IGNORE, existing);
        }
        PowerOutage updated = updatePowerOutage(existing, parsedOutage, canonicalAddresses);
        PowerOutage saved = powerOutagePort.save(updated);
        return new ResolutionResult(Decision.UPDATE, saved);
    }

    private PowerOutage createPowerOutage(ParsedOutage parsedOutage, List<Address> canonicalAddresses) {
        Set<UUID> seen = new java.util.HashSet<>();
        Collection<PowerOutageAddress> poas = canonicalAddresses.stream()
                .filter(addr -> seen.add(addr.id()))
                .map(addr -> PowerOutageAddress.unboundOf(UUID.randomUUID(), addr))
                .collect(Collectors.toList());
        return PowerOutage.of(UUID.randomUUID(), parsedOutage.source(), parsedOutage.startTime(), parsedOutage.endTime(), parsedOutage.reason(), "АКТИВНО", poas);
    }

    private PowerOutage updatePowerOutage(PowerOutage existing, ParsedOutage parsedOutage, List<Address> canonicalAddresses) {
        Collection<PowerOutageAddress> poas = canonicalAddresses.stream()
                .map(addr -> PowerOutageAddress.unboundOf(UUID.randomUUID(), addr))
                .collect(Collectors.toList());
        // For fallback identity, startTime is part of identity and is same as existing; for external, startTime is mutable
        Instant newStartTime = existing.startTime();
        // If externalReference identity, allow startTime update
        if (parsedOutage.externalReference() != null && !parsedOutage.externalReference().isBlank()) {
            newStartTime = parsedOutage.startTime();
        }
        return PowerOutage.of(existing.id(), existing.source(), newStartTime, parsedOutage.endTime(), parsedOutage.reason(), existing.status(), poas);
    }

    private boolean isSameData(PowerOutage existing, ParsedOutage parsedOutage, List<Address> canonicalAddresses) {
        if (!existing.endTime().equals(parsedOutage.endTime())) {
            return false;
        }
        if (!existing.reason().equals(parsedOutage.reason())) {
            return false;
        }
        Set<UUID> existingIds = existing.addresses().stream().map(a -> a.address().id()).collect(Collectors.toSet());
        Set<UUID> newIds = canonicalAddresses.stream().map(Address::id).collect(Collectors.toSet());
        return existingIds.equals(newIds);
    }

    public enum Decision {
        CREATE, UPDATE, IGNORE
    }

    public record ResolutionResult(Decision decision, PowerOutage powerOutage) {}
}
