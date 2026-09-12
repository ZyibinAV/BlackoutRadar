package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEngineTest {

    static class StubAdapter implements DeliveryPort {
        private final String type;
        private DeliveryResult result = DeliveryResult.success();
        private RuntimeException failure;
        private final List<DeliveredCall> calls = new ArrayList<>();

        StubAdapter(String type) {
            this.type = type;
        }

        @Override
        public String channelType() {
            return type;
        }

        @Override
        public DeliveryResult deliver(NotificationChannel channel, String message) {
            calls.add(new DeliveredCall(channel, message));
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    record DeliveredCall(NotificationChannel channel, String message) {
    }

    @Mock private NotificationPort notificationPort;
    @Mock private NotificationChannelPort channelPort;

    private StubAdapter emailAdapter;
    private StubAdapter telegramAdapter;
    private NotificationEngine engine;

    private User user;
    private PowerOutage powerOutage;
    private Subscription subscription;
    private Notification pending;

    private static final String MESSAGE =
            "Power outage: 2026-01-01T00:00:00Z - 2026-01-01T02:00:00Z. Reason: reason";

    @BeforeEach
    void setUp() {
        emailAdapter = new StubAdapter("email");
        telegramAdapter = new StubAdapter("telegram");
        engine = new NotificationEngine(notificationPort, channelPort,
                new DeliveryChannelRegistry(List.of(emailAdapter, telegramAdapter)));

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
        when(notificationPort.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void pendingAllChannelsSucceedBecomesSent() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        NotificationChannel telegram = channel("telegram", "123456789", true);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email, telegram));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals(pending.id(), result.id());
        assertEquals(MESSAGE, result.message());
        assertEquals(1, emailAdapter.calls.size());
        assertEquals(1, telegramAdapter.calls.size());
        assertSame(email, emailAdapter.calls.get(0).channel());
        assertEquals(MESSAGE, emailAdapter.calls.get(0).message());
        assertSame(telegram, telegramAdapter.calls.get(0).channel());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort, times(1)).save(captor.capture());
        assertEquals(NotificationStatus.SENT, captor.getValue().status());
        verify(notificationPort).claimForProcessing(pending.id());
        verify(notificationPort, never()).findById(any(UUID.class));
    }

    @Test
    void oneChannelFailureBecomesFailedButOthersProcessed() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        NotificationChannel telegram = channel("telegram", "123456789", true);
        telegramAdapter.result = DeliveryResult.temporaryFailure();
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email, telegram));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        assertEquals(pending.id(), result.id());
        assertEquals(1, emailAdapter.calls.size());
        assertEquals(1, telegramAdapter.calls.size());
    }

    @Test
    void noEnabledChannelsBecomesFailedWithoutDelivery() {
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(
                channel("email", "personal@example.com", false),
                channel("telegram", "123456789", false)));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        assertTrue(emailAdapter.calls.isEmpty());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void absentChannelsBecomesFailedWithoutDelivery() {
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of());

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        assertTrue(emailAdapter.calls.isEmpty());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void onlyEnabledChannelsAreProcessed() {
        NotificationChannel enabled = channel("email", "personal@example.com", true);
        NotificationChannel disabled = channel("telegram", "123456789", false);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(enabled, disabled));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals(1, emailAdapter.calls.size());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void unknownChannelTypeFailsChannelButOthersContinue() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        NotificationChannel sms = channel("sms", "+70000000000", true);
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email, sms));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        assertEquals(1, emailAdapter.calls.size());
    }

    @Test
    void adapterExceptionFailsChannelButOthersContinue() {
        NotificationChannel email = channel("email", "personal@example.com", true);
        NotificationChannel telegram = channel("telegram", "123456789", true);
        emailAdapter.failure = new RuntimeException("smtp down");
        stubSuccessfulClaim();
        when(channelPort.findByUserId(user.id())).thenReturn(List.of(email, telegram));

        Notification result = engine.process(pending.id());

        assertEquals(NotificationStatus.FAILED, result.status());
        assertEquals(1, emailAdapter.calls.size());
        assertEquals(1, telegramAdapter.calls.size());
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
        verifyNoInteractions(channelPort);
        assertTrue(emailAdapter.calls.isEmpty());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void processingIsNotReprocessed() {
        Notification processing = pending.startProcessing();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(processing));

        Notification result = engine.process(pending.id());

        assertSame(processing, result);
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort);
        assertTrue(emailAdapter.calls.isEmpty());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void sentIsSkipped() {
        Notification sent = pending.startProcessing().markSent();
        when(notificationPort.claimForProcessing(pending.id())).thenReturn(Optional.empty());
        when(notificationPort.findById(pending.id())).thenReturn(Optional.of(sent));

        Notification result = engine.process(pending.id());

        assertSame(sent, result);
        verify(notificationPort, never()).save(any(Notification.class));
        verifyNoInteractions(channelPort);
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
        verifyNoInteractions(channelPort);
        assertTrue(emailAdapter.calls.isEmpty());
        assertTrue(telegramAdapter.calls.isEmpty());
    }

    @Test
    void missingNotificationThrows() {
        UUID id = UUID.randomUUID();
        when(notificationPort.claimForProcessing(id)).thenReturn(Optional.empty());
        when(notificationPort.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> engine.process(id));
        verifyNoInteractions(channelPort);
        verify(notificationPort, never()).save(any(Notification.class));
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class, () -> engine.process(null));
        verifyNoInteractions(notificationPort, channelPort);
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
        verifyNoMoreInteractions(notificationPort, channelPort);
    }
}
