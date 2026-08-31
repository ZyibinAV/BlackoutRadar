package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PowerOutageMatchingService {

    private final PowerOutagePort powerOutagePort;
    private final MatchingBoundary matchingBoundary;

    public PowerOutageMatchingService(PowerOutagePort powerOutagePort, MatchingBoundary matchingBoundary) {
        this.powerOutagePort = powerOutagePort;
        this.matchingBoundary = matchingBoundary;
    }

    @Transactional(readOnly = true)
    public void match(UUID powerOutageId) {
        PowerOutage powerOutage = powerOutagePort.findById(powerOutageId)
                .orElseThrow(() -> new NoSuchElementException("PowerOutage not found: " + powerOutageId));
        matchingBoundary.handle(powerOutage);
    }
}
