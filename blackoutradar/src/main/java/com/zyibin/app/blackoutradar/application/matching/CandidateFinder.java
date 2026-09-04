package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionSearchPort;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CandidateFinder {

    private final SubscriptionSearchPort subscriptionSearchPort;

    public CandidateFinder(SubscriptionSearchPort subscriptionSearchPort) {
        this.subscriptionSearchPort = Objects.requireNonNull(subscriptionSearchPort, "subscriptionSearchPort must not be null");
    }

    public List<Candidate> findCandidates(PowerOutage powerOutage) {
        Objects.requireNonNull(powerOutage, "powerOutage must not be null");
        Set<UUID> addressIds = powerOutage.addresses().stream()
                .map(poa -> poa.address().id())
                .collect(Collectors.toSet());
        if (addressIds.isEmpty()) {
            return List.of();
        }
        return subscriptionSearchPort.findActiveByAddressIds(addressIds).stream()
                .map(Candidate::new)
                .toList();
    }
}
