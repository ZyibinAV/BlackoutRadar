package com.zyibin.app.blackoutradar.domain.notification;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.Objects;
import java.util.UUID;

public final class Notification {

    private final UUID id;
    private final Subscription subscription;
    private final PowerOutage powerOutage;
    private final String message;
    private final NotificationStatus status;

    private Notification(UUID id, Subscription subscription, PowerOutage powerOutage,
                         String message, NotificationStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.powerOutage = Objects.requireNonNull(powerOutage, "powerOutage must not be null");
        this.message = DomainPreconditions.requireNotBlank(message, "message must not be blank");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static Notification of(UUID id, Subscription subscription, PowerOutage powerOutage,
                                  String message) {
        return new Notification(id, subscription, powerOutage, message, NotificationStatus.PENDING);
    }

    public static Notification of(UUID id, Subscription subscription, PowerOutage powerOutage,
                                  String message, NotificationStatus status) {
        return new Notification(id, subscription, powerOutage, message, status);
    }

    public Notification startProcessing() {
        if (status != NotificationStatus.PENDING && status != NotificationStatus.FAILED) {
            throw new IllegalStateException(
                    "notification " + id + " cannot start processing from status " + status);
        }
        return withStatus(NotificationStatus.PROCESSING);
    }

    public Notification markSent() {
        if (status != NotificationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "notification " + id + " cannot be marked as sent from status " + status);
        }
        return withStatus(NotificationStatus.SENT);
    }

    public Notification markFailed() {
        if (status != NotificationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "notification " + id + " cannot be marked as failed from status " + status);
        }
        return withStatus(NotificationStatus.FAILED);
    }

    private Notification withStatus(NotificationStatus newStatus) {
        return new Notification(id, subscription, powerOutage, message, newStatus);
    }

    public UUID id() {
        return id;
    }

    public Subscription subscription() {
        return subscription;
    }

    public PowerOutage powerOutage() {
        return powerOutage;
    }

    public String message() {
        return message;
    }

    public NotificationStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification that)) {
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
        return "Notification{"
                + "id=" + id
                + ", subscription=" + subscription
                + ", powerOutage=" + powerOutage
                + ", message='" + message + '\''
                + ", status=" + status
                + '}';
    }
}