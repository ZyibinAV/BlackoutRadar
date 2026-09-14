package com.zyibin.app.blackoutradar.domain.notification.port;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPort {

    Optional<Notification> findById(UUID id);

    Optional<Notification> findBySubscriptionAndPowerOutage(UUID subscriptionId, UUID powerOutageId);

    Notification save(Notification notification);

    /**
     * Captures the notification for processing if it is still pending.
     * Returns the captured notification in processing state,
     * or empty when it can no longer be captured.
     */
    Optional<Notification> claimForProcessing(UUID id);

    /**
     * Returns the notification holding a PostgreSQL row lock on it until the
     * surrounding transaction commits. Intended only for the short
     * finalization decision (see ADR-014): lock row, re-read deliveries,
     * decide, save, commit. No delivery or network work may run while
     * the lock is held.
     */
    Optional<Notification> lockById(UUID id);
}
