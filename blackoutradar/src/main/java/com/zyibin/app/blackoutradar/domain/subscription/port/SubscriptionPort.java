package com.zyibin.app.blackoutradar.domain.subscription.port;

import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPort {

    Optional<Subscription> findById(UUID id);

    Subscription save(Subscription subscription);
}