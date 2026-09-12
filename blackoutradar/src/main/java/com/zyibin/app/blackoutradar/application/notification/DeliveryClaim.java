package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import java.util.Objects;
import java.util.UUID;

/**
 * Technical result of an atomic claim.
 * Carries the claimed delivery in PROCESSING state together with
 * the unique ownership token issued to the successful claimant.
 * The token is a concurrency fencing mechanism and is not part
 * of the NotificationDelivery business model.
 */
public record DeliveryClaim(NotificationDelivery delivery, UUID ownershipToken) {

    public DeliveryClaim {
        Objects.requireNonNull(delivery, "delivery must not be null");
        Objects.requireNonNull(ownershipToken, "ownershipToken must not be null");
    }
}
