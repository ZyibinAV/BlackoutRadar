package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.Objects;

public record Candidate(Subscription subscription) {
    public Candidate {
        Objects.requireNonNull(subscription, "subscription must not be null");
    }
}
