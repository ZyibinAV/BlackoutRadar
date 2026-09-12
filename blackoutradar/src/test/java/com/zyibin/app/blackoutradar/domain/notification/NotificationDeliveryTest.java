package com.zyibin.app.blackoutradar.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.outage.OutageTestData;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private Notification notification() {
        Subscription subscription = Subscription.of(UUID.randomUUID(), user(), OutageTestData.address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
        PowerOutage powerOutage = OutageTestData.outage();
        return Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
    }

    private NotificationChannel channel(User user, String type, String destination) {
        return NotificationChannel.of(UUID.randomUUID(), user, type, destination, true);
    }

    @Test
    void validCreationStartsReadyWithoutNextAttempt() {
        Notification notification = notification();
        NotificationChannel channel = channel(user(), "email", "personal@example.com");
        UUID id = UUID.randomUUID();

        NotificationDelivery delivery = NotificationDelivery.of(id, notification, channel);

        assertEquals(id, delivery.id());
        assertSame(notification, delivery.notification());
        assertSame(channel, delivery.notificationChannel());
        assertEquals(DeliveryStatus.READY, delivery.status());
        assertNull(delivery.nextAttemptAt());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> NotificationDelivery.of(null, notification(), channel(user(), "email", "a@example.com")));
    }

    @Test
    void nullNotificationRejected() {
        assertThrows(NullPointerException.class,
                () -> NotificationDelivery.of(UUID.randomUUID(), null, channel(user(), "email", "a@example.com")));
    }

    @Test
    void nullChannelRejected() {
        assertThrows(NullPointerException.class,
                () -> NotificationDelivery.of(UUID.randomUUID(), notification(), null));
    }

    @Test
    void referencesExactChannelNotOnlyType() {
        User user = user();
        NotificationChannel first = channel(user, "email", "personal@example.com");
        NotificationChannel second = channel(user, "email", "work@example.com");
        Notification notification = notification();

        NotificationDelivery delivery = NotificationDelivery.of(UUID.randomUUID(), notification, second);

        assertSame(second, delivery.notificationChannel());
        assertEquals("work@example.com", delivery.notificationChannel().destination());
    }

    @Test
    void readyToProcessingTransition() {
        NotificationDelivery delivery = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com"));

        NotificationDelivery processing = delivery.startProcessing();

        assertEquals(DeliveryStatus.PROCESSING, processing.status());
        assertEquals(delivery.id(), processing.id());
        assertNull(processing.nextAttemptAt());
    }

    @Test
    void processingToSentTransition() {
        NotificationDelivery processing = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com")).startProcessing();

        NotificationDelivery sent = processing.markSent();

        assertEquals(DeliveryStatus.SENT, sent.status());
    }

    @Test
    void processingToFailedTransition() {
        NotificationDelivery processing = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com")).startProcessing();

        NotificationDelivery failed = processing.markFailed();

        assertEquals(DeliveryStatus.FAILED, failed.status());
    }

    @Test
    void processingToReadySchedulesRetry() {
        NotificationDelivery processing = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com")).startProcessing();
        Instant nextAttemptAt = NOW.plus(5, ChronoUnit.MINUTES);

        NotificationDelivery retry = processing.scheduleRetry(nextAttemptAt);

        assertEquals(DeliveryStatus.READY, retry.status());
        assertEquals(nextAttemptAt, retry.nextAttemptAt());
    }

    @Test
    void scheduleRetryRequiresNextAttemptAt() {
        NotificationDelivery processing = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com")).startProcessing();

        assertThrows(NullPointerException.class, () -> processing.scheduleRetry(null));
    }

    @Test
    void invalidTransitionsRejected() {
        NotificationDelivery ready = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com"));

        assertThrows(IllegalStateException.class, ready::markSent);
        assertThrows(IllegalStateException.class, ready::markFailed);
        assertThrows(IllegalStateException.class, () -> ready.scheduleRetry(NOW));

        NotificationDelivery sent = ready.startProcessing().markSent();
        assertThrows(IllegalStateException.class, sent::startProcessing);
        assertThrows(IllegalStateException.class, sent::markSent);
        assertThrows(IllegalStateException.class, sent::markFailed);
        assertThrows(IllegalStateException.class, () -> sent.scheduleRetry(NOW));

        NotificationDelivery failed = NotificationDelivery.of(UUID.randomUUID(), notification(),
                channel(user(), "email", "a@example.com")).startProcessing().markFailed();
        assertThrows(IllegalStateException.class, failed::startProcessing);
        assertThrows(IllegalStateException.class, () -> failed.scheduleRetry(NOW));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Notification notification = notification();
        NotificationChannel channel = channel(user(), "email", "a@example.com");

        NotificationDelivery a = NotificationDelivery.of(id, notification, channel);
        NotificationDelivery b = NotificationDelivery.of(id, notification, channel,
                DeliveryStatus.PROCESSING, NOW);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, NotificationDelivery.of(UUID.randomUUID(), notification, channel));
    }

    @Test
    void toStringContainsOnlyTechnicalFields() {
        UUID id = UUID.randomUUID();
        NotificationDelivery delivery = NotificationDelivery.of(id, notification(),
                channel(user(), "email", "personal-s3cret@example.com")).startProcessing();

        String text = delivery.toString();

        assertTrue(text.contains(id.toString()));
        assertTrue(text.contains("PROCESSING"));
        assertFalse(text.contains("personal-s3cret@example.com"));
        assertFalse(text.contains("message"));
        assertFalse(text.contains("notification="));
        assertFalse(text.contains("notificationChannel="));
    }

    @Test
    void modelContainsNoRetryInfrastructureState() {
        Set<String> fields = Arrays.stream(NotificationDelivery.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "notification", "notificationChannel", "status", "nextAttemptAt"), fields);
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("retry")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("attempt")
                && !name.equals("nextAttemptAt")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("destination")));
    }
}
