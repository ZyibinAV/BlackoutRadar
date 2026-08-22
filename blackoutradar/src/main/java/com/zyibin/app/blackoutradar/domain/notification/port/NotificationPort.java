package com.zyibin.app.blackoutradar.domain.notification.port;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPort {

    Optional<Notification> findById(UUID id);

    Optional<Notification> findBySubscriptionAndPowerOutage(UUID subscriptionId, UUID powerOutageId);

    Notification save(Notification notification);
}
