package com.zyibin.app.blackoutradar.domain.notification.port;

import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryPort {

    Optional<NotificationDelivery> findById(UUID id);

    List<NotificationDelivery> findByNotificationId(UUID notificationId);

    /**
     * Creates a new delivery. State changes of an existing delivery during
     * processing must go only through the fencing API
     * ({@code NotificationDeliveryFencingPort}), never through this method,
     * so a stale worker cannot overwrite a delivery it no longer owns.
     *
     * @throws IllegalStateException when a delivery with the same id already exists
     */
    NotificationDelivery save(NotificationDelivery delivery);

    /**
     * Captures the delivery for processing if it is ready and its next attempt is due.
     * A missing next attempt time means the delivery is due immediately.
     * Returns the captured delivery in processing state,
     * or empty when it can no longer be captured.
     */
    Optional<NotificationDelivery> claimForProcessing(UUID id, Instant now);

    /**
     * Returns up to {@code limit} deliveries ready for processing:
     * in ready state with a missing or due next attempt time.
     */
    List<NotificationDelivery> findReadyForProcessing(Instant now, int limit);

    /**
     * Returns up to {@code limit} deliveries that look stuck:
     * in processing state with an incomplete attempt started before the given threshold.
     * Does not change any state; recovery of the returned deliveries is a separate step.
     */
    List<NotificationDelivery> findStuckDeliveries(Instant stuckBefore, int limit);
}
