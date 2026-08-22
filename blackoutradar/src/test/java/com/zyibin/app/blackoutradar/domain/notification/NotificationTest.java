package com.zyibin.app.blackoutradar.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.outage.OutageTestData;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private Subscription subscription() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        return Subscription.of(UUID.randomUUID(), user, OutageTestData.address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
    }

    private PowerOutage powerOutage() {
        return OutageTestData.outage();
    }

    @Test
    void validNotificationCreation() {
        Subscription subscription = subscription();
        PowerOutage powerOutage = powerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "По вашему адресу ожидается отключение электроэнергии");

        assertEquals(NotificationStatus.PENDING, notification.status());
        assertEquals("По вашему адресу ожидается отключение электроэнергии", notification.message());
        assertSame(subscription, notification.subscription());
        assertSame(powerOutage, notification.powerOutage());
    }

    @Test
    void initialStatusIsPending() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение");

        assertEquals(NotificationStatus.PENDING, notification.status());
    }

    @Test
    void explicitStatusAccepted() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение", NotificationStatus.PROCESSING);

        assertEquals(NotificationStatus.PROCESSING, notification.status());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> Notification.of(null, subscription(), powerOutage(), "Сообщение"));
    }

    @Test
    void nullSubscriptionRejected() {
        assertThrows(NullPointerException.class,
                () -> Notification.of(UUID.randomUUID(), null, powerOutage(), "Сообщение"));
    }

    @Test
    void nullPowerOutageRejected() {
        assertThrows(NullPointerException.class,
                () -> Notification.of(UUID.randomUUID(), subscription(), null, "Сообщение"));
    }

    @Test
    void nullMessageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Notification.of(UUID.randomUUID(), subscription(), powerOutage(), null));
    }

    @Test
    void blankMessageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Notification.of(UUID.randomUUID(), subscription(), powerOutage(), "   "));
    }

    @Test
    void nullStatusRejected() {
        assertThrows(NullPointerException.class,
                () -> Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                        "Сообщение", null));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        Subscription subscription = subscription();
        PowerOutage powerOutage = powerOutage();
        Notification a = Notification.of(id, subscription, powerOutage, "Сообщение");
        Notification b = Notification.of(id, subscription, powerOutage, "Сообщение");

        assertNotEquals(a, Notification.of(UUID.randomUUID(), subscription, powerOutage, "Сообщение"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void pendingToProcessingTransition() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение");
        Notification processing = notification.startProcessing();

        assertEquals(NotificationStatus.PENDING, notification.status());
        assertEquals(NotificationStatus.PROCESSING, processing.status());
    }

    @Test
    void processingToSentTransition() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение", NotificationStatus.PROCESSING);
        Notification sent = notification.markSent();

        assertEquals(NotificationStatus.PROCESSING, notification.status());
        assertEquals(NotificationStatus.SENT, sent.status());
    }

    @Test
    void processingToFailedTransition() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение", NotificationStatus.PROCESSING);
        Notification failed = notification.markFailed();

        assertEquals(NotificationStatus.PROCESSING, notification.status());
        assertEquals(NotificationStatus.FAILED, failed.status());
    }

    @Test
    void sentIsFinal() {
        Notification sent = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение", NotificationStatus.SENT);

        assertThrows(IllegalStateException.class, sent::startProcessing);
        assertThrows(IllegalStateException.class, sent::markSent);
        assertThrows(IllegalStateException.class, sent::markFailed);
    }

    @Test
    void sentCannotBeReachedFromPendingDirectly() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение");

        assertThrows(IllegalStateException.class, notification::markSent);
        assertThrows(IllegalStateException.class, notification::markFailed);
    }

    @Test
    void failedCanBeProcessedAgain() {
        Notification failed = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение", NotificationStatus.FAILED);
        Notification processing = failed.startProcessing();

        assertEquals(NotificationStatus.PROCESSING, processing.status());
    }

    @Test
    void reprocessingDoesNotCreateNewNotification() {
        UUID id = UUID.randomUUID();
        Subscription subscription = subscription();
        PowerOutage powerOutage = powerOutage();
        Notification original = Notification.of(id, subscription, powerOutage, "Сообщение");

        Notification failed = original.startProcessing().markFailed();
        Notification reprocessed = failed.startProcessing().markSent();

        assertEquals(id, reprocessed.id());
        assertEquals(original, reprocessed);
        assertNotEquals(original.status(), reprocessed.status());
    }

    @Test
    void identityIsPreservedAcrossTransitions() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.of(id, subscription(), powerOutage(), "Сообщение");

        assertEquals(id, notification.startProcessing().id());
        assertEquals(id, notification.startProcessing().markSent().id());
    }

    @Test
    void notificationDoesNotRequireMatch() {
        Notification notification = Notification.of(UUID.randomUUID(), subscription(), powerOutage(),
                "Сообщение");

        assertEquals(1, Arrays.stream(notification.getClass().getDeclaredFields())
                .filter(field -> field.getName().equals("subscription"))
                .count());
        assertEquals(1, Arrays.stream(notification.getClass().getDeclaredFields())
                .filter(field -> field.getName().equals("powerOutage"))
                .count());
        assertEquals(0, Arrays.stream(notification.getClass().getDeclaredFields())
                .filter(field -> field.getName().toLowerCase().contains("match"))
                .count());
    }

    @Test
    void noRetryOrDeliveryStateExists() {
        Set<String> fields = Arrays.stream(Notification.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "subscription", "powerOutage", "message", "status"), fields);
    }

    @Test
    void noRetryOrDeliveryMethodsExist() {
        Set<String> methods = Arrays.stream(Notification.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("retry")));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("attempt")));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("backoff")));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("delivery")));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("schedul")));
    }

    @Test
    void uniquenessRepresentedBySubscriptionAndPowerOutage() {
        Subscription subscription = subscription();
        PowerOutage powerOutage = powerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "Сообщение");

        assertSame(subscription, notification.subscription());
        assertSame(powerOutage, notification.powerOutage());
        assertNotEquals(subscription.id(), powerOutage.id());
        assertFalse(Arrays.stream(notification.getClass().getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("match")));
    }
}