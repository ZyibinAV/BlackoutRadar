package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import java.time.Instant;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PowerOutageService {

    private final PowerOutagePort powerOutagePort;
    private final SourcePort sourcePort;

    public PowerOutageService(PowerOutagePort powerOutagePort, SourcePort sourcePort) {
        this.powerOutagePort = powerOutagePort;
        this.sourcePort = sourcePort;
    }

    @Transactional
    public PowerOutage create(UUID sourceId,
                              Instant startTime,
                              Instant endTime,
                              String reason,
                              String status,
                              Collection<PowerOutageAddress> addresses) {
        Source source = sourcePort.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source not found: " + sourceId));
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), source, startTime, endTime, reason, status, addresses);
        return powerOutagePort.save(outage);
    }

    @Transactional(readOnly = true)
    public Optional<PowerOutage> findById(UUID id) {
        return powerOutagePort.findById(id);
    }

    @Transactional(readOnly = true)
    public PowerOutage getById(UUID id) {
        return powerOutagePort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("PowerOutage not found: " + id));
    }

    @Transactional
    public PowerOutage update(UUID outageId,
                              Instant startTime,
                              Instant endTime,
                              String reason,
                              String status,
                              Collection<PowerOutageAddress> addresses) {
        PowerOutage existing = getById(outageId);
        PowerOutage updated = PowerOutage.of(
                existing.id(),
                existing.source(),
                startTime,
                endTime,
                reason,
                status,
                addresses);
        return powerOutagePort.save(updated);
    }
}
