package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.DeliveryAttemptPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class RetryProcessingServiceTest {

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

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NEXT = Instant.parse("2026-01-01T00:01:00Z");
    private static final String MESSAGE =
            "Power outage: 2026-01-01T00:00:00Z - 2026-01-01T02:00:00Z. Reason: reason";

    @Mock private NotificationDeliveryPort deliveryPort;
    @Mock private NotificationDeliveryFencingPort fencingPort;
    @Mock private DeliveryAttemptPort attemptPort;
    @Mock private RetryPolicy retryPolicy;
    @Mock private NotificationFinalizationService finalizationService;

    private StubAdapter emailAdapter;
    private RetryProcessingService service;

    private User user;
    private NotificationDelivery delivery;
    private NotificationChannel channel;

    @BeforeEach
    void setUp() {
        emailAdapter = new StubAdapter("email");
        service = new RetryProcessingService(deliveryPort, fencingPort, attemptPort,
                new DeliveryChannelRegistry(List.of(emailAdapter)), retryPolicy, finalizationService);

        user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
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
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage, MESSAGE);
        channel = NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", true);
        delivery = NotificationDelivery.of(UUID.randomUUID(), notification, channel);
    }

    private void stubClaimSuccess() {
        NotificationDelivery processing = delivery.startProcessing();
        UUID token = UUID.randomUUID();
        when(fencingPort.claim(delivery.id(), NOW))
                .thenReturn(Optional.of(new DeliveryClaim(processing, token)));
        when(attemptPort.save(any(DeliveryAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fencingPort.saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));
        when(finalizationService.finalizeNotification(delivery.notification().id()))
                .thenAnswer(invocation -> delivery.notification());
    }

    @Test
    void successfulClaimProcessesDeliveryInOrder() {
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertEquals(DeliveryStatus.SENT, result.status());
        assertEquals(delivery.id(), result.id());
        InOrder inOrder = inOrder(fencingPort, attemptPort, retryPolicy, finalizationService);
        inOrder.verify(fencingPort).claim(delivery.id(), NOW);
        inOrder.verify(attemptPort).nextAttemptNumber(delivery.id());
        inOrder.verify(attemptPort, times(2)).save(any(DeliveryAttempt.class));
        inOrder.verify(fencingPort).saveIfOwned(eq(delivery.id()), any(UUID.class),
                any(NotificationDelivery.class));
        inOrder.verify(finalizationService).finalizeNotification(delivery.notification().id());
        verifyNoMoreInteractions(deliveryPort, fencingPort, attemptPort, retryPolicy, finalizationService);
    }

    @Test
    void firstAttemptGetsNumberOne() {
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        service.process(delivery.id(), NOW);

        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptPort, times(2)).save(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).attemptNumber());
        assertEquals(1, captor.getAllValues().get(1).attemptNumber());
    }

    @Test
    void nextAttemptGetsNextNumber() {
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(2);

        service.process(delivery.id(), NOW);

        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptPort, times(2)).save(captor.capture());
        assertEquals(2, captor.getAllValues().get(0).attemptNumber());
        assertEquals(2, captor.getAllValues().get(1).attemptNumber());
    }

    @Test
    void startedAttemptSavedBeforeDeliveryAndCompletedAfter() {
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        service.process(delivery.id(), NOW);

        assertEquals(1, emailAdapter.calls.size());
        assertSame(channel, emailAdapter.calls.get(0).channel());
        assertEquals(MESSAGE, emailAdapter.calls.get(0).message());
        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptPort, times(2)).save(captor.capture());
        DeliveryAttempt started = captor.getAllValues().get(0);
        DeliveryAttempt completed = captor.getAllValues().get(1);
        assertEquals(started.id(), completed.id());
        assertNull(started.completedAt());
        assertNull(started.result());
        assertEquals(DeliveryAttemptResult.SUCCESS, completed.result());
        assertTrue(completed.isCompleted());
    }

    @Test
    void successBecomesSentWithoutPolicy() {
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        DeliveryProcessingOutcome outcome = service.process(delivery.id(), NOW);

        assertEquals(DeliveryStatus.SENT, outcome.delivery().status());
        assertTrue(outcome.fencedCompletion());
        verify(finalizationService).finalizeNotification(delivery.notification().id());
        verifyNoInteractions(retryPolicy);
    }

    @Test
    void completionTimeComesFromClockWhilePolicyUsesProcessingNow() {
        Instant completedAt = NOW.plusSeconds(30);
        RetryProcessingService clockedService = new RetryProcessingService(deliveryPort, fencingPort,
                attemptPort, new DeliveryChannelRegistry(List.of(emailAdapter)), retryPolicy,
                finalizationService, Clock.fixed(completedAt, ZoneOffset.UTC));
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        clockedService.process(delivery.id(), NOW);

        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptPort, times(2)).save(captor.capture());
        DeliveryAttempt started = captor.getAllValues().get(0);
        DeliveryAttempt completed = captor.getAllValues().get(1);
        assertEquals(NOW, started.startedAt());
        assertEquals(completedAt, completed.completedAt());
        assertTrue(!completed.completedAt().isBefore(started.startedAt()));
        verifyNoInteractions(retryPolicy);
    }

    @Test
    void nullClockRejected() {
        assertThrows(NullPointerException.class, () -> new RetryProcessingService(deliveryPort,
                fencingPort, attemptPort, new DeliveryChannelRegistry(List.of(emailAdapter)),
                retryPolicy, finalizationService, null));
    }

    @Test
    void nullFinalizationServiceRejected() {
        assertThrows(NullPointerException.class, () -> new RetryProcessingService(deliveryPort,
                fencingPort, attemptPort, new DeliveryChannelRegistry(List.of(emailAdapter)),
                retryPolicy, null, Clock.systemUTC()));
    }

    @Test
    void temporaryFailureWithAllowedRetryBecomesReady() {
        emailAdapter.result = DeliveryResult.temporaryFailure();
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);
        when(retryPolicy.decide(eq(DeliveryAttemptResult.TEMPORARY_FAILURE), eq(1), eq(NOW)))
                .thenReturn(RetryDecision.retryAt(NEXT));

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertEquals(DeliveryStatus.READY, result.status());
        assertEquals(NEXT, result.nextAttemptAt());
        verify(retryPolicy).decide(eq(DeliveryAttemptResult.TEMPORARY_FAILURE), eq(1), eq(NOW));
    }

    @Test
    void temporaryFailureWithForbiddenRetryBecomesFailed() {
        emailAdapter.result = DeliveryResult.temporaryFailure();
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(3);
        when(retryPolicy.decide(eq(DeliveryAttemptResult.TEMPORARY_FAILURE), eq(3), eq(NOW)))
                .thenReturn(RetryDecision.noRetry());

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertEquals(DeliveryStatus.FAILED, result.status());
        verify(retryPolicy).decide(eq(DeliveryAttemptResult.TEMPORARY_FAILURE), eq(3), eq(NOW));
    }

    @Test
    void permanentFailureBecomesFailedWithoutPolicy() {
        emailAdapter.result = DeliveryResult.permanentFailure();
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertEquals(DeliveryStatus.FAILED, result.status());
        assertEquals(1, emailAdapter.calls.size());
        verifyNoInteractions(retryPolicy);
    }

    @Test
    void unknownChannelTypeBecomesFailedWithoutDelivery() {
        NotificationChannel sms = NotificationChannel.of(UUID.randomUUID(), user, "sms",
                "+70000000000", true);
        NotificationDelivery smsDelivery = NotificationDelivery.of(UUID.randomUUID(),
                delivery.notification(), sms);
        NotificationDelivery smsProcessing = smsDelivery.startProcessing();
        UUID smsToken = UUID.randomUUID();
        when(fencingPort.claim(smsDelivery.id(), NOW))
                .thenReturn(Optional.of(new DeliveryClaim(smsProcessing, smsToken)));
        when(attemptPort.nextAttemptNumber(smsDelivery.id())).thenReturn(1);
        when(attemptPort.save(any(DeliveryAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fencingPort.saveIfOwned(eq(smsDelivery.id()), eq(smsToken), any(NotificationDelivery.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));
        when(finalizationService.finalizeNotification(smsDelivery.notification().id()))
                .thenAnswer(invocation -> smsDelivery.notification());

        DeliveryProcessingOutcome outcome = service.process(smsDelivery.id(), NOW);

        assertEquals(DeliveryStatus.FAILED, outcome.delivery().status());
        assertTrue(outcome.fencedCompletion());
        assertTrue(emailAdapter.calls.isEmpty());
        verifyNoInteractions(retryPolicy);
    }

    @Test
    void adapterExceptionBecomesTemporaryFailure() {
        emailAdapter.failure = new RuntimeException("smtp down");
        stubClaimSuccess();
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);
        when(retryPolicy.decide(eq(DeliveryAttemptResult.TEMPORARY_FAILURE), eq(1), eq(NOW)))
                .thenReturn(RetryDecision.retryAt(NEXT));

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertEquals(DeliveryStatus.READY, result.status());
        assertEquals(NEXT, result.nextAttemptAt());
        assertEquals(1, emailAdapter.calls.size());
    }

    @Test
    void lostClaimSkipsDeliveryAndAttempt() {
        NotificationDelivery processing = delivery.startProcessing();
        when(fencingPort.claim(delivery.id(), NOW)).thenReturn(Optional.empty());
        when(deliveryPort.findById(delivery.id())).thenReturn(Optional.of(processing));

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertSame(processing, result);
        verify(fencingPort).claim(delivery.id(), NOW);
        verify(deliveryPort).findById(delivery.id());
        verifyNoInteractions(attemptPort, retryPolicy, finalizationService);
        verify(fencingPort, never()).saveIfOwned(any(UUID.class), any(UUID.class),
                any(NotificationDelivery.class));
        assertTrue(emailAdapter.calls.isEmpty());
    }

    @Test
    void lostOwnershipDoesNotOverwriteDelivery() {
        NotificationDelivery processing = delivery.startProcessing();
        UUID token = UUID.randomUUID();
        when(fencingPort.claim(delivery.id(), NOW))
                .thenReturn(Optional.of(new DeliveryClaim(processing, token)));
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);
        when(attemptPort.save(any(DeliveryAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fencingPort.saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class)))
                .thenReturn(Optional.empty());
        NotificationDelivery current = delivery.startProcessing();
        when(deliveryPort.findById(delivery.id())).thenReturn(Optional.of(current));

        NotificationDelivery result = service.process(delivery.id(), NOW).delivery();

        assertSame(current, result);
        verify(fencingPort).claim(delivery.id(), NOW);
        verify(attemptPort, times(2)).save(any(DeliveryAttempt.class));
        verify(fencingPort).saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class));
        verify(deliveryPort).findById(delivery.id());
        verifyNoInteractions(finalizationService);
        assertEquals(1, emailAdapter.calls.size());
    }

    @Test
    void staleWorkerDoesNotTriggerFinalization() {
        NotificationDelivery processing = delivery.startProcessing();
        UUID token = UUID.randomUUID();
        when(fencingPort.claim(delivery.id(), NOW))
                .thenReturn(Optional.of(new DeliveryClaim(processing, token)));
        when(attemptPort.nextAttemptNumber(delivery.id())).thenReturn(1);
        when(attemptPort.save(any(DeliveryAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fencingPort.saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class)))
                .thenReturn(Optional.empty());
        when(deliveryPort.findById(delivery.id())).thenReturn(Optional.of(processing));

        DeliveryProcessingOutcome outcome = service.process(delivery.id(), NOW);

        assertFalse(outcome.fencedCompletion());
        verifyNoInteractions(finalizationService);
    }

    @Test
    void missingDeliveryThrows() {
        UUID id = UUID.randomUUID();
        when(fencingPort.claim(id, NOW)).thenReturn(Optional.empty());
        when(deliveryPort.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.process(id, NOW));
        verifyNoInteractions(attemptPort, retryPolicy, finalizationService);
        assertTrue(emailAdapter.calls.isEmpty());
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> service.process(null, NOW));
        assertThrows(NullPointerException.class, () -> service.process(delivery.id(), null));
        verifyNoInteractions(deliveryPort, fencingPort, attemptPort, retryPolicy, finalizationService);
    }

    @Test
    void serviceContainsNoChannelSpecificLogic() {
        Stream.of(RetryProcessingService.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .forEach(name -> {
                    assertFalse(name.contains("email"), "Service must not contain " + name);
                    assertFalse(name.contains("telegram"), "Service must not contain " + name);
                    assertFalse(name.contains("smtp"), "Service must not contain " + name);
                    assertFalse(name.contains("sms"), "Service must not contain " + name);
                });
        verifyNoMoreInteractions(deliveryPort, fencingPort, attemptPort, retryPolicy, finalizationService);
    }
}
