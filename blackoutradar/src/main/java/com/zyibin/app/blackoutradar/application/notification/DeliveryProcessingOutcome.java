package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import java.util.Objects;

/**
 * Result of one Retry Processing run.
 *
 * {@code fencedCompletion} is true only when this worker has successfully
 * persisted the final state of the delivery with its own ownership token.
 * Only then the worker is allowed to initiate {@code Notification} finalization
 * (see ADR-014); a stale worker that lost ownership must not finalize.
 */
public record DeliveryProcessingOutcome(NotificationDelivery delivery, boolean fencedCompletion) {

    public DeliveryProcessingOutcome {
        Objects.requireNonNull(delivery, "delivery must not be null");
    }
}
