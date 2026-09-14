package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationFinalizationServiceTest {

    @Mock private NotificationPort notificationPort;
    @Mock private NotificationDeliveryPort deliveryPort;

    private NotificationFinalizationService service;

    private Notification notification;
    private NotificationChannel firstChannel;
    private NotificationChannel secondChannel;

    @BeforeEach
    void setUp() {
        service = new NotificationFinalizationService(notificationPort, deliveryPort);

        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        PowerOutage powerOutage = PowerOutage.of(UUID.randomUUID(), source, start, end, "reason",
                "АКТИВНО", List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription subscription = Subscription.of(UUID.randomUUID(), user, address, start, end,
                true, end.plusSeconds(3600));
        Notification base = Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
        notification = base.startProcessing();
        firstChannel = NotificationChannel.of(UUID.randomUUID(), user, "email",
                "personal@example.com", true);
        secondChannel = NotificationChannel.of(UUID.randomUUID(), user, "telegram",
                "123456789", true);
    }

    private NotificationDelivery delivery(NotificationChannel channel, DeliveryStatus status) {
        return NotificationDelivery.of(UUID.randomUUID(), notification, channel, status, null);
    }

    private void stubLock(Notification current) {
        when(notificationPort.lockById(notification.id())).thenReturn(Optional.of(current));
    }

    private void stubSave() {
        when(notificationPort.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void allSentFinalizesSent() {
        stubLock(notification);
        stubSave();
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT),
                        delivery(secondChannel, DeliveryStatus.SENT)));

        Notification result = service.finalizeNotification(notification.id());

        assertEquals(NotificationStatus.SENT, result.status());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertEquals(NotificationStatus.SENT, captor.getValue().status());
    }

    @Test
    void activeDeliveryKeepsProcessingWithoutSave() {
        stubLock(notification);
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT),
                        delivery(secondChannel, DeliveryStatus.READY)));

        Notification result = service.finalizeNotification(notification.id());

        assertSame(notification, result);
        verify(notificationPort, never()).save(any(Notification.class));
    }

    @Test
    void processingDeliveryKeepsProcessingWithoutSave() {
        stubLock(notification);
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT),
                        delivery(secondChannel, DeliveryStatus.PROCESSING)));

        Notification result = service.finalizeNotification(notification.id());

        assertSame(notification, result);
        verify(notificationPort, never()).save(any(Notification.class));
    }

    @Test
    void completedWithFailedFinalizesFailed() {
        stubLock(notification);
        stubSave();
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT),
                        delivery(secondChannel, DeliveryStatus.FAILED)));

        Notification result = service.finalizeNotification(notification.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertEquals(NotificationStatus.FAILED, captor.getValue().status());
    }

    @Test
    void noDeliveriesFinalizesFailed() {
        stubLock(notification);
        stubSave();
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(List.of());

        Notification result = service.finalizeNotification(notification.id());

        assertEquals(NotificationStatus.FAILED, result.status());
    }

    @Test
    void alreadySentSkipsSave() {
        Notification sent = notification.markSent();
        when(notificationPort.lockById(notification.id())).thenReturn(Optional.of(sent));
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT)));

        Notification result = service.finalizeNotification(notification.id());

        assertSame(sent, result);
        verify(notificationPort, never()).save(any(Notification.class));
    }

    @Test
    void pendingNotificationTransitionsThroughProcessing() {
        Notification pending = Notification.of(UUID.randomUUID(), notification.subscription(),
                notification.powerOutage(), notification.message());
        when(notificationPort.lockById(notification.id())).thenReturn(Optional.of(pending));
        when(notificationPort.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryPort.findByNotificationId(notification.id())).thenReturn(
                List.of(delivery(firstChannel, DeliveryStatus.SENT)));

        Notification result = service.finalizeNotification(notification.id());

        assertEquals(NotificationStatus.SENT, result.status());
    }

    @Test
    void missingNotificationThrows() {
        UUID id = UUID.randomUUID();
        when(notificationPort.lockById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.finalizeNotification(id));
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> service.finalizeNotification(null));
    }
}
