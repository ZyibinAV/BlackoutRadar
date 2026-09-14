package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives the aggregate state of a {@code Notification} from the current states
 * of all its {@code NotificationDelivery} records (see ADR-014).
 *
 * <p>The whole decision runs in a single short transaction holding a PostgreSQL
 * row lock on the notification: lock row, re-read deliveries, decide, save if
 * changed, commit. No delivery, network call, retry processing or attempt
 * creation happens while the lock is held. Must be invoked only after a
 * successful fenced completion of a delivery; a stale worker must never call it.
 */
@Service
public class NotificationFinalizationService {

    private final NotificationPort notificationPort;
    private final NotificationDeliveryPort deliveryPort;

    public NotificationFinalizationService(NotificationPort notificationPort,
                                           NotificationDeliveryPort deliveryPort) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort must not be null");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
    }

    @Transactional
    public Notification finalizeNotification(UUID notificationId) {
        Objects.requireNonNull(notificationId, "notificationId must not be null");
        Notification current = notificationPort.lockById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        NotificationStatus target = decide(deliveryPort.findByNotificationId(notificationId));
        if (target == current.status()) {
            return current;
        }
        Notification processing = current.status() == NotificationStatus.PROCESSING
                ? current
                : current.startProcessing();
        Notification updated = target == NotificationStatus.SENT
                ? processing.markSent()
                : processing.markFailed();
        return notificationPort.save(updated);
    }

    private NotificationStatus decide(List<NotificationDelivery> deliveries) {
        if (deliveries.isEmpty()) {
            return NotificationStatus.FAILED;
        }
        boolean hasFailed = false;
        for (NotificationDelivery delivery : deliveries) {
            if (delivery.status() == DeliveryStatus.READY
                    || delivery.status() == DeliveryStatus.PROCESSING) {
                return NotificationStatus.PROCESSING;
            }
            if (delivery.status() == DeliveryStatus.FAILED) {
                hasFailed = true;
            }
        }
        if (hasFailed) {
            return NotificationStatus.FAILED;
        }
        return NotificationStatus.SENT;
    }
}
