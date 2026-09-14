package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEngineTest {

    @Mock private NotificationPort notificationPort;
    @Mock private NotificationChannelPort channelPort;
    @Mock private NotificationDeliveryPort deliveryPort;
    @Mock private RetryProcessingService retryProcessingService;
    @Mock private NotificationFinalizationService finalizationService;

    private NotificationEngine engine;

    private User user;
    private PowerOutage powerOutage;
    private Subscription subscription;
    private Notification pending;

    private static final String MESSAGE =
            "Power outage: 2026-01-01T00:00:00Z - 2026-01-01T02:00:00Z. Reason: reason";

    @BeforeEach
    void setUp() {
        engine = new NotificationEngine(notificationPort, channelPort, deliveryPort,
                retryProcessingService, finalizationService);

        user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        powerOutage = PowerOutage.of(UUID.randomUUID(), source, start, end, "reason", "АКТИВНО",
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        subscription = Subscription.of(UUID.randomUUID(), user, address, start, end, true, end.plusSeconds(3600));
        pending = Notification.of(UUID.randomUUID(), subscription, powerOutage, MESSAGE);
    }

    private NotificationChannel channel(String type, String destination, boolean enabled) {
        return NotificationChannel.of(UUID.randomUUID(), user, type, destination, enabled);
    }

    private void stubSuccessfulClaim() {
        when(notificationPort.claimForProcessing(pending.id()))
                .thenReturn(Optional.of(pending.startProcessing()));
    }

    private void stubDeliverySaving() {
        when(deliveryPort.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSuccessfulRetry() {
        when(retryProcessingService.process(any(UUID.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    UUID deliveryId = invocation.getArgument(0);
                    Notification claimed = pending.startProcessing();
                    NotificationDelivery sent = NotificationDelivery.of(deliveryId, claimed,
                            channel("email", "personal@example.com", true),
                            DeliveryStatus.SENT, null);
                    return new DeliveryProcessingOutcome(sent, true);
                });
    }

    @Test
    void pendingCreatesDeliveryPerEnabledChannelAndProcessesThem() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        NotificationChannel telegram = channel("telegram", "123456789", true);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email, telegram));
        stubDeliverySaving();
        stubSuccessfulRetry();
        Notification sent = pending.startProcessing().markSent();
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(sent));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals(pending.id(), result.id());
        assertEquals(MESSAGE, result.message());
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryPort, times(2)).save(captor.capture());
        List<NotificationDelivery> created = captor.getAllValues();
        assertEquals(List.of(email.id(), telegram.id()),
                created.stream().map(delivery -> delivery.notificationChannel().id()).toList());
        assertTrue(created.stream()
                .allMatch(delivery -> delivery.status() == DeliveryStatus.READY));
        assertTrue(created.stream()
                .allMatch(delivery -> MESSAGE.equals(delivery.notification().message())));
        assertEquals(2, created.stream().map(NotificationDelivery::id).distinct().count());
        verify(retryProcessingService, times(2)).process(any(UUID.class), any(Instant.class));
        verify(retryProcessingService).process(eq(created.get(0).id()), any(Instant.class));
        verify(retryProcessingService).process(eq(created.get(1).id()), any(Instant.class));
        verifyNoInteractions(finalizationService);
    }

    @Test
    void sameTypeChannelsProduceIndependentDeliveries() {
        NotificationChannel personal = channel("email", "personal@example.com", true);
        NotificationChannel work = channel("email", "work@example.com", true);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(personal, work));
        stubDeliverySaving();
        stubSuccessfulRetry();
        when(notificationPort.findById(pending.id()))
                .thenReturn(Optional.of(pending.startProcessing().markSent()));

        engine.process(pending.id());

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryPort, times(2)).save(captor.capture());
        assertEquals(List.of(personal.id(), work.id()),
                captor.getAllValues().stream()
                        .map(delivery -> delivery.notificationChannel().id()).toList());
    }

    @Test
    void onlyEnabledChannelsProduceDeliveries() {
        NotificationChannel enabled = channel("email", "personal@example.com", true);
        NotificationChannel disabled = channel("telegram", "123456789", false);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(enabled, disabled));
        stubDeliverySaving();
        stubSuccessfulRetry();
        when(notificationPort.findById(pending.id()))
                .thenReturn(Optional.of(pending.startProcessing().markSent()));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.SENT, result.status());
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryPort, times(1)).save(captor.capture());
        assertEquals(enabled.id(), captor.getValue().notificationChannel().id());
        verify(retryProcessingService, times(1)).process(any(UUID.class), any(Instant.class));
    }

    @Test
    void noEnabledChannelsFinalizesWithoutDelivery() {
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(
                channel("email", "personal@example.com", false)));
        Notification failed = pending.startProcessing().markFailed();
        when(finalizationService.finalizeNotification(pending.id())).thenReturn(failed);

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        verify(deliveryPort, never()).save(any(NotificationDelivery.class));
        verifyNoInteractions(retryProcessingService);
        verify(finalizationService).finalizeNotification(pending.id());
    }

    @Test
    void staleDeliveryOutcomeDoesNotTriggerFinalization() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email));
        stubDeliverySaving();
        NotificationDelivery processingView = NotificationDelivery.of(UUID.randomUUID(),
                pending.startProcessing(), email, DeliveryStatus.PROCESSING, null);
        when(retryProcessingService.process(any(UUID.class), any(Instant.class)))
                .thenReturn(new DeliveryProcessingOutcome(processingView, false));
        Notification current = pending.startProcessing();
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(current));

        Notification result = engine.process(pending.id());

        assertSame(current, result);
        verify(retryProcessingService, times(1)).process(any(UUID.class), any(Instant.class));
        verifyNoInteractions(finalizationService);
    }

    @Test
    void lostClaimSkipsDelivery() {
        Notification processing = pending.startProcessing();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(processing));

        Notification result = engine.process(pending.id());

        assertSame(processing, result);
        verify(notificationPort).claimForProcessing(pending.id());
        verify(notificationPort).findById(pending.id());
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort, deliveryPort, retryProcessingService, finalizationService);
    }

    @Test
    void processingIsNotReprocessed() {
        Notification processing = pending.startProcessing();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(processing));

        Notification result = engine.process(pending.id());

        assertSame(processing, result);
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort, deliveryPort, retryProcessingService, finalizationService);
    }

    @Test
    void sentIsSkipped() {
        Notification sent = pending.startProcessing().markSent();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(sent));

        Notification result = engine.process(pending.id());

        assertSame(sent, result);
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort, deliveryPort, retryProcessingService, finalizationService);
    }

    @Test
    void failedDoesNotRetry() {
        Notification failed = pending.startProcessing().markFailed();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(failed));

        Notification result = engine.process(pending.id());

        assertSame(failed, result);
        assertEquals(NotificationStatus.FAILED, result.status());
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort, deliveryPort, retryProcessingService, finalizationService);
    }

    @Test
    void missingNotificationThrows() {
        UUID id = UUID.randomUUID();
        when(notificationPort.claimForProcessing(id)).thenReturn(Optional.empty());
        when(notificationPort.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> engine.process(id));
        verifyNoInteractions(channelPort, deliveryPort, retryProcessingService, finalizationService);
        verify(notificationPort, never()).save(any(Notification.class));
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> engine.process(null));
        verifyNoInteractions(notificationPort, channelPort, deliveryPort,
                retryProcessingService, finalizationService);
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class,
                () -> new NotificationEngine(null, channelPort, deliveryPort,
                        retryProcessingService, finalizationService));
        assertThrows(NullPointerException.class,
                () -> new NotificationEngine(notificationPort, null, deliveryPort,
                        retryProcessingService, finalizationService));
        assertThrows(NullPointerException.class,
                () -> new NotificationEngine(notificationPort, channelPort, null,
                        retryProcessingService, finalizationService));
        assertThrows(NullPointerException.class,
                () -> new NotificationEngine(notificationPort, channelPort, deliveryPort,
                        null, finalizationService));
        assertThrows(NullPointerException.class,
                () -> new NotificationEngine(notificationPort, channelPort, deliveryPort,
                        retryProcessingService, null));
    }

    @Test
    void engineHasNoDeliveryOrLockDependencies() {
        Set<String> forbidden = Set.of(
                "com.zyibin.app.blackoutradar.application.notification.DeliveryPort",
                "com.zyibin.app.blackoutradar.application.notification.DeliveryChannelRegistry",
                "java.util.concurrent.locks.ReentrantLock",
                "java.util.concurrent.locks.Lock",
                "java.util.concurrent.ConcurrentHashMap",
                "java.util.concurrent.ConcurrentMap");
        for (Field field : NotificationEngine.class.getDeclaredFields()) {
            String typeName = field.getType().getName();
            assertFalse(forbidden.contains(typeName),
                    "Engine must not depend on " + typeName);
        }
        Stream.of(NotificationEngine.class.getDeclaredMethods())
                .forEach(method -> assertFalse(Modifier.isSynchronized(method.getModifiers()),
                        "Engine must not use synchronized " + method.getName()));
        verifyNoInteractions(notificationPort, channelPort, deliveryPort,
                retryProcessingService, finalizationService);
    }

    @Test
    void engineContainsNoChannelSpecificLogic() {
        Stream.of(NotificationEngine.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .forEach(name -> {
                    assertFalse(name.contains("email"), "Engine must not contain " + name);
                    assertFalse(name.contains("telegram"), "Engine must not contain " + name);
                    assertFalse(name.contains("smtp"), "Engine must not contain " + name);
                    assertFalse(name.contains("sms"), "Engine must not contain " + name);
                });
        verifyNoMoreInteractions(notificationPort, channelPort, deliveryPort,
                retryProcessingService, finalizationService);
    }
}
