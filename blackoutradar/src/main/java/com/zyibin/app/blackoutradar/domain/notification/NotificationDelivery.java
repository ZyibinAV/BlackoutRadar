package com.zyibin.app.blackoutradar.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NotificationDelivery {

    private final UUID id;
    private final Notification notification;
    private final NotificationChannel notificationChannel;
    private final DeliveryStatus status;
    private final Instant nextAttemptAt;

    private NotificationDelivery(UUID id, Notification notification, NotificationChannel notificationChannel,
                                 DeliveryStatus status, Instant nextAttemptAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.notification = Objects.requireNonNull(notification, "notification must not be null");
        this.notificationChannel = Objects.requireNonNull(notificationChannel, "notificationChannel must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.nextAttemptAt = nextAttemptAt;
    }

    public static NotificationDelivery of(UUID id, Notification notification,
                                          NotificationChannel notificationChannel) {
        return new NotificationDelivery(id, notification, notificationChannel, DeliveryStatus.READY, null);
    }

    public static NotificationDelivery of(UUID id, Notification notification,
                                          NotificationChannel notificationChannel,
                                          DeliveryStatus status, Instant nextAttemptAt) {
        return new NotificationDelivery(id, notification, notificationChannel, status, nextAttemptAt);
    }

    public NotificationDelivery startProcessing() {
        if (status != DeliveryStatus.READY) {
            throw new IllegalStateException(
                    "delivery " + id + " cannot start processing from status " + status);
        }
        return withStatus(DeliveryStatus.PROCESSING, nextAttemptAt);
    }

    public NotificationDelivery markSent() {
        if (status != DeliveryStatus.PROCESSING) {
            throw new IllegalStateException(
                    "delivery " + id + " cannot be marked as sent from status " + status);
        }
        return withStatus(DeliveryStatus.SENT, nextAttemptAt);
    }

    public NotificationDelivery markFailed() {
        if (status != DeliveryStatus.PROCESSING) {
            throw new IllegalStateException(
                    "delivery " + id + " cannot be marked as failed from status " + status);
        }
        return withStatus(DeliveryStatus.FAILED, nextAttemptAt);
    }

    public NotificationDelivery scheduleRetry(Instant nextAttemptAt) {
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        if (status != DeliveryStatus.PROCESSING) {
            throw new IllegalStateException(
                    "delivery " + id + " cannot be scheduled for retry from status " + status);
        }
        return withStatus(DeliveryStatus.READY, nextAttemptAt);
    }

    private NotificationDelivery withStatus(DeliveryStatus newStatus, Instant newNextAttemptAt) {
        return new NotificationDelivery(id, notification, notificationChannel, newStatus, newNextAttemptAt);
    }

    public UUID id() {
        return id;
    }

    public Notification notification() {
        return notification;
    }

    public NotificationChannel notificationChannel() {
        return notificationChannel;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationDelivery that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "NotificationDelivery{"
                + "id=" + id
                + ", notification=" + notification
                + ", notificationChannel=" + notificationChannel
                + ", status=" + status
                + ", nextAttemptAt=" + nextAttemptAt
                + '}';
    }
}
