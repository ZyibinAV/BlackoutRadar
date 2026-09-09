package com.zyibin.app.blackoutradar.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationChannelTest {

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    @Test
    void validCreation() {
        User user = user();
        UUID id = UUID.randomUUID();

        NotificationChannel channel = NotificationChannel.of(id, user, "email", "personal@example.com", true);

        assertEquals(id, channel.id());
        assertEquals(user, channel.user());
        assertEquals("email", channel.type());
        assertEquals("personal@example.com", channel.destination());
        assertTrue(channel.isEnabled());
    }

    @Test
    void disabledStatePreserved() {
        NotificationChannel channel = NotificationChannel.of(UUID.randomUUID(), user(),
                "telegram", "123456789", false);

        assertFalse(channel.isEnabled());
    }

    @Test
    void nullUserRejected() {
        assertThrows(NullPointerException.class, () -> NotificationChannel.of(
                UUID.randomUUID(), null, "email", "personal@example.com", true));
    }

    @Test
    void nullTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> NotificationChannel.of(
                UUID.randomUUID(), user(), null, "personal@example.com", true));
    }

    @Test
    void blankTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> NotificationChannel.of(
                UUID.randomUUID(), user(), "   ", "personal@example.com", true));
    }

    @Test
    void nullDestinationRejected() {
        assertThrows(IllegalArgumentException.class, () -> NotificationChannel.of(
                UUID.randomUUID(), user(), "email", null, true));
    }

    @Test
    void blankDestinationRejected() {
        assertThrows(IllegalArgumentException.class, () -> NotificationChannel.of(
                UUID.randomUUID(), user(), "email", "  ", true));
    }

    @Test
    void sameUserMayHaveMultipleChannelsOfSameType() {
        User user = user();

        NotificationChannel personal = NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal@example.com", true);
        NotificationChannel work = NotificationChannel.of(UUID.randomUUID(), user,
                "email", "work@example.com", true);
        NotificationChannel telegram = NotificationChannel.of(UUID.randomUUID(), user,
                "telegram", "123456789", true);

        assertNotEquals(personal, work);
        assertEquals(user, work.user());
        assertEquals("email", work.type());
        assertEquals("telegram", telegram.type());
    }

    @Test
    void channelTypeIsExtensibleIdentifier() {
        User user = user();

        NotificationChannel email = NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal@example.com", true);
        NotificationChannel telegram = NotificationChannel.of(UUID.randomUUID(), user,
                "telegram", "123456789", true);
        NotificationChannel future = NotificationChannel.of(UUID.randomUUID(), user,
                "future-channel", "future-destination", true);

        assertEquals("email", email.type());
        assertEquals("telegram", telegram.type());
        assertEquals("future-channel", future.type());
        assertEquals("future-destination", future.destination());
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        User user = user();

        NotificationChannel a = NotificationChannel.of(id, user, "email", "personal@example.com", true);
        NotificationChannel b = NotificationChannel.of(id, user, "email", "work@example.com", false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal@example.com", true));
    }
}
