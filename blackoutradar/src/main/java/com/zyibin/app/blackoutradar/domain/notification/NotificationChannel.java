package com.zyibin.app.blackoutradar.domain.notification;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import com.zyibin.app.blackoutradar.domain.identity.User;
import java.util.Objects;
import java.util.UUID;

public final class NotificationChannel {

    private final UUID id;
    private final User user;
    private final String type;
    private final String destination;
    private final boolean enabled;

    private NotificationChannel(UUID id, User user, String type, String destination, boolean enabled) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.type = DomainPreconditions.requireNotBlank(type, "type must not be blank");
        this.destination = DomainPreconditions.requireNotBlank(destination, "destination must not be blank");
        this.enabled = enabled;
    }

    public static NotificationChannel of(UUID id, User user, String type, String destination,
                                         boolean enabled) {
        return new NotificationChannel(id, user, type, destination, enabled);
    }

    public UUID id() {
        return id;
    }

    public User user() {
        return user;
    }

    public String type() {
        return type;
    }

    public String destination() {
        return destination;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationChannel that)) {
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
        return "NotificationChannel{"
                + "id=" + id
                + ", user=" + user
                + ", type='" + type + '\''
                + ", destination='" + destination + '\''
                + ", enabled=" + enabled
                + '}';
    }
}
