package com.zyibin.app.blackoutradar.domain.notification.port;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptPort {

    List<DeliveryAttempt> findByNotificationDeliveryId(UUID notificationDeliveryId);

    DeliveryAttempt save(DeliveryAttempt attempt);

    /**
     * Returns the next attempt number for the delivery.
     * Intended to be used after a successful claim of the delivery;
     * the unique constraint on (delivery, attempt number) remains the physical guard.
     */
    int nextAttemptNumber(UUID notificationDeliveryId);
}
