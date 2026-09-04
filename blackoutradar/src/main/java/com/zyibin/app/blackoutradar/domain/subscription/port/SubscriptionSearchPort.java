package com.zyibin.app.blackoutradar.domain.subscription.port;

import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionSearchPort {

    List<Subscription> findActiveByAddressIds(Set<UUID> addressIds);
}
