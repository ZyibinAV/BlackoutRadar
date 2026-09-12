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

class DeliveryAttemptTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private NotificationDelivery delivery() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Subscription subscription = Subscription.of(UUID.randomUUID(), user, OutageTestData.address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
        PowerOutage powerOutage = OutageTestData.outage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
        NotificationChannel channel = NotificationChannel.of(UUID.randomUUID(), user, "telegram",
                "123456789", true);
        return NotificationDelivery.of(UUID.randomUUID(), notification, channel);
    }

    @Test
    void startedAttemptHasNoResult() {
        NotificationDelivery delivery = delivery();
        UUID id = UUID.randomUUID();

        DeliveryAttempt attempt = DeliveryAttempt.started(id, delivery, 1, NOW);

        assertEquals(id, attempt.id());
        assertSame(delivery, attempt.notificationDelivery());
        assertEquals(1, attempt.attemptNumber());
        assertEquals(NOW, attempt.startedAt());
        assertNull(attempt.completedAt());
        assertNull(attempt.result());
        assertNull(attempt.errorCode());
        assertFalse(attempt.isCompleted());
    }

    @Test
    void completedAttemptHoldsResult() {
        NotificationDelivery delivery = delivery();
        Instant completedAt = NOW.plus(2, ChronoUnit.SECONDS);

        DeliveryAttempt attempt = DeliveryAttempt.completed(UUID.randomUUID(), delivery, 2,
                NOW, completedAt, DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT");

        assertEquals(2, attempt.attemptNumber());
        assertEquals(completedAt, attempt.completedAt());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, attempt.result());
        assertEquals("SMTP_TIMEOUT", attempt.errorCode());
        assertTrue(attempt.isCompleted());
    }

    @Test
    void startedAttemptCanBeCompleted() {
        NotificationDelivery delivery = delivery();
        DeliveryAttempt started = DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, NOW);
        Instant completedAt = NOW.plus(1, ChronoUnit.SECONDS);

        DeliveryAttempt completed = started.complete(completedAt, DeliveryAttemptResult.SUCCESS, null);

        assertEquals(DeliveryAttemptResult.SUCCESS, completed.result());
        assertEquals(completedAt, completed.completedAt());
        assertNull(completed.errorCode());
        assertEquals(started.id(), completed.id());
        assertEquals(1, completed.attemptNumber());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> DeliveryAttempt.started(null, delivery(), 1, NOW));
    }

    @Test
    void nullDeliveryRejected() {
        assertThrows(NullPointerException.class,
                () -> DeliveryAttempt.started(UUID.randomUUID(), null, 1, NOW));
    }

    @Test
    void invalidAttemptNumberRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryAttempt.started(UUID.randomUUID(), delivery(), 0, NOW));
    }

    @Test
    void nullStartedAtRejected() {
        assertThrows(NullPointerException.class,
                () -> DeliveryAttempt.started(UUID.randomUUID(), delivery(), 1, null));
    }

    @Test
    void completedAtBeforeStartedAtRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryAttempt.completed(UUID.randomUUID(), delivery(), 1,
                        NOW, NOW.minusSeconds(1), DeliveryAttemptResult.SUCCESS, null));
    }

    @Test
    void completeRequiresResult() {
        DeliveryAttempt started = DeliveryAttempt.started(UUID.randomUUID(), delivery(), 1, NOW);

        assertThrows(NullPointerException.class,
                () -> started.complete(NOW.plusSeconds(1), null, null));
    }

    @Test
    void doubleCompleteRejected() {
        DeliveryAttempt completed = DeliveryAttempt.completed(UUID.randomUUID(), delivery(), 1,
                NOW, NOW.plusSeconds(1), DeliveryAttemptResult.SUCCESS, null);

        assertThrows(IllegalStateException.class,
                () -> completed.complete(NOW.plusSeconds(2), DeliveryAttemptResult.SUCCESS, null));
    }

    @Test
    void attemptNumbersAreIndependentPerDelivery() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();

        DeliveryAttempt firstAttempt = DeliveryAttempt.started(UUID.randomUUID(), first, 1, NOW);
        DeliveryAttempt secondAttempt = DeliveryAttempt.started(UUID.randomUUID(), second, 1, NOW);

        assertEquals(1, firstAttempt.attemptNumber());
        assertEquals(1, secondAttempt.attemptNumber());
        assertSame(first, firstAttempt.notificationDelivery());
        assertSame(second, secondAttempt.notificationDelivery());
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        NotificationDelivery delivery = delivery();

        DeliveryAttempt a = DeliveryAttempt.started(id, delivery, 1, NOW);
        DeliveryAttempt b = DeliveryAttempt.completed(id, delivery, 3,
                NOW, NOW.plusSeconds(5), DeliveryAttemptResult.SUCCESS, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, NOW));
    }

    @Test
    void toStringExposesNoDeliveryDetails() {
        NotificationDelivery delivery = delivery();
        UUID id = UUID.randomUUID();
        DeliveryAttempt attempt = DeliveryAttempt.completed(id, delivery, 2,
                NOW, NOW.plusSeconds(5), DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT");

        String text = attempt.toString();

        assertTrue(text.contains(id.toString()));
        assertTrue(text.contains("attemptNumber=2"));
        assertTrue(text.contains("TEMPORARY_FAILURE"));
        assertTrue(text.contains("SMTP_TIMEOUT"));
        assertFalse(text.contains(delivery.id().toString()));
        assertFalse(text.contains("notificationDelivery="));
        assertFalse(text.contains("123456789"));
        assertFalse(text.contains("message"));
    }

    @Test
    void attemptHoldsNoRetryStateOrUserData() {
        Set<String> fields = Arrays.stream(DeliveryAttempt.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "notificationDelivery", "attemptNumber", "startedAt",
                "completedAt", "result", "errorCode"), fields);
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("destination")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("message")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("nextattempt")));
    }
}
