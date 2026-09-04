package com.zyibin.app.blackoutradar.domain.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.Objects;

public record Match(
        Subscription subscription,
        PowerOutage powerOutage
) {
    public Match {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(powerOutage, "powerOutage must not be null");
    }
}
