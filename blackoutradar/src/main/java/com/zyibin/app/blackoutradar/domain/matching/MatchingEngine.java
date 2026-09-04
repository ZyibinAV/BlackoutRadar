package com.zyibin.app.blackoutradar.domain.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MatchingEngine {

    public List<Match> match(PowerOutage powerOutage, List<Subscription> subscriptions) {
        Objects.requireNonNull(powerOutage, "powerOutage must not be null");
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        Set<UUID> matchedSubscriptionIds = new HashSet<>();
        for (Subscription subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }
            if (matchedSubscriptionIds.contains(subscription.id())) {
                continue;
            }
            if (matchesSubscription(powerOutage, subscription)) {
                matchedSubscriptionIds.add(subscription.id());
                matches.add(new Match(subscription, powerOutage));
            }
        }
        return List.copyOf(matches);
    }

    private boolean matchesSubscription(PowerOutage powerOutage, Subscription subscription) {
        if (!subscription.isActive()) {
            return false;
        }
        boolean addressStationMatched = false;
        for (PowerOutageAddress outageAddress : powerOutage.addresses()) {
            if (!outageAddress.address().equals(subscription.address())) {
                continue;
            }
            if (!matchesTransformerStation(subscription, outageAddress)) {
                continue;
            }
            addressStationMatched = true;
            break;
        }
        if (!addressStationMatched) {
            return false;
        }
        return overlaps(
                subscription.monitoringStart(), subscription.monitoringEnd(),
                powerOutage.startTime(), powerOutage.endTime());
    }

    private boolean matchesTransformerStation(Subscription subscription, PowerOutageAddress outageAddress) {
        if (!subscription.hasTransformerStations()) {
            return true;
        }
        if (outageAddress.transformerStation() == null) {
            return true;
        }
        return subscription.transformerStations().contains(outageAddress.transformerStation());
    }

    private boolean overlaps(java.time.Instant monitoringStart, java.time.Instant monitoringEnd,
                             java.time.Instant outageStart, java.time.Instant outageEnd) {
        return monitoringStart.isBefore(outageEnd) && outageStart.isBefore(monitoringEnd);
    }
}
