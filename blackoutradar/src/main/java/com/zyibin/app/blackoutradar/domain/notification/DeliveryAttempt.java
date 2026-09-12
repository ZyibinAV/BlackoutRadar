package com.zyibin.app.blackoutradar.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DeliveryAttempt {

    private final UUID id;
    private final NotificationDelivery notificationDelivery;
    private final int attemptNumber;
    private final Instant startedAt;
    private final Instant completedAt;
    private final DeliveryAttemptResult result;
    private final String errorCode;

    private DeliveryAttempt(UUID id, NotificationDelivery notificationDelivery, int attemptNumber,
                            Instant startedAt, Instant completedAt, DeliveryAttemptResult result,
                            String errorCode) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.notificationDelivery = Objects.requireNonNull(notificationDelivery, "notificationDelivery must not be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        this.attemptNumber = attemptNumber;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (completedAt != null && result == null) {
            throw new IllegalArgumentException("result must not be null when completedAt is set");
        }
        if (result != null && completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null when result is set");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        this.completedAt = completedAt;
        this.result = result;
        this.errorCode = errorCode;
    }

    public static DeliveryAttempt started(UUID id, NotificationDelivery notificationDelivery,
                                          int attemptNumber, Instant startedAt) {
        return new DeliveryAttempt(id, notificationDelivery, attemptNumber, startedAt, null, null, null);
    }

    public static DeliveryAttempt completed(UUID id, NotificationDelivery notificationDelivery,
                                            int attemptNumber, Instant startedAt, Instant completedAt,
                                            DeliveryAttemptResult result, String errorCode) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(result, "result must not be null");
        return new DeliveryAttempt(id, notificationDelivery, attemptNumber, startedAt,
                completedAt, result, errorCode);
    }

    public DeliveryAttempt complete(Instant completedAt, DeliveryAttemptResult result, String errorCode) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(result, "result must not be null");
        if (this.completedAt != null) {
            throw new IllegalStateException("attempt " + id + " is already completed");
        }
        return new DeliveryAttempt(id, notificationDelivery, attemptNumber, startedAt,
                completedAt, result, errorCode);
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID id() {
        return id;
    }

    public NotificationDelivery notificationDelivery() {
        return notificationDelivery;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public DeliveryAttemptResult result() {
        return result;
    }

    public String errorCode() {
        return errorCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeliveryAttempt that)) {
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
        return "DeliveryAttempt{"
                + "id=" + id
                + ", notificationDelivery=" + notificationDelivery
                + ", attemptNumber=" + attemptNumber
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", result=" + result
                + ", errorCode='" + errorCode + '\''
                + '}';
    }
}
