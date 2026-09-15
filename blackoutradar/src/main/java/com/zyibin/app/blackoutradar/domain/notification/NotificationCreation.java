package com.zyibin.app.blackoutradar.domain.notification;

import java.util.Objects;

/**
 * Result of an atomic get-or-create for {@code Notification}.
 *
 * <p>{@code created} is true only when this call has inserted the row;
 * otherwise the already existing canonical notification is returned and
 * the caller must not treat it as newly created.
 */
public record NotificationCreation(Notification notification, boolean created) {

    public NotificationCreation {
        Objects.requireNonNull(notification, "notification must not be null");
    }
}
