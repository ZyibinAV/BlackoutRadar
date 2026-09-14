package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.notification.DeliveryClaim;
import com.zyibin.app.blackoutradar.application.notification.DeliveryProcessingOutcome;
import com.zyibin.app.blackoutradar.application.notification.DeliveryPort;
import com.zyibin.app.blackoutradar.application.notification.DeliveryResult;
import com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort;
import com.zyibin.app.blackoutradar.application.notification.RetryProcessingService;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.DeliveryAttemptPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.DeliveryAttemptJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationDeliveryJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * End-to-end Retry checks against real PostgreSQL, real persistence adapters,
 * real RetryProcessingService and real schedulers. Only the external
 * DeliveryPort is stubbed; no real sending, no secrets, no user data in logs.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RetryProcessingIntegrationTest.StubDeliveryConfiguration.class})
class RetryProcessingIntegrationTest {

    static class StubAdapter implements DeliveryPort {
        final AtomicInteger deliveries = new AtomicInteger();
        volatile DeliveryResult result = DeliveryResult.success();
        volatile long delayMillis;

        @Override
        public String channelType() {
            return "test-retry";
        }

        @Override
        public DeliveryResult deliver(NotificationChannel channel, String message) {
            deliveries.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }
    }

    @TestConfiguration
    static class StubDeliveryConfiguration {
        @Bean
        StubAdapter stubAdapter() {
            return new StubAdapter();
        }
    }

    @Autowired
    private RetryProcessingService processingService;

    @Autowired
    private RetryScheduler retryScheduler;

    @Autowired
    private RecoveryScheduler recoveryScheduler;

    @Autowired
    private NotificationDeliveryPort deliveryPort;

    @Autowired
    private NotificationDeliveryFencingPort fencingPort;

    @Autowired
    private DeliveryAttemptPort attemptPort;

    @Autowired
    private NotificationPort notificationPort;

    @Autowired
    private NotificationChannelPort channelPort;

    @Autowired
    private UserPort userPort;

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private StreetPort streetPort;

    @Autowired
    private AddressPort addressPort;

    @Autowired
    private SubscriptionPort subscriptionPort;

    @Autowired
    private SourcePort sourcePort;

    @Autowired
    private PowerOutagePort powerOutagePort;

    @Autowired
    private NotificationDeliveryJpaRepository deliveryRepository;

    @Autowired
    private DeliveryAttemptJpaRepository attemptRepository;

    @Autowired
    private StubAdapter stubAdapter;

    private final List<UUID> createdDeliveryIds = new ArrayList<>();
    private final List<UUID> createdAttemptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID deliveryId : createdDeliveryIds) {
            for (DeliveryAttempt attempt : attemptPort.findByNotificationDeliveryId(deliveryId)) {
                if (!createdAttemptIds.contains(attempt.id())) {
                    createdAttemptIds.add(attempt.id());
                }
            }
        }
        attemptRepository.deleteAllById(createdAttemptIds);
        deliveryRepository.deleteAllById(createdDeliveryIds);
        createdAttemptIds.clear();
        createdDeliveryIds.clear();
    }

    // 1. Successful delivery full cycle.
    @Test
    void successfulDeliveryCompletesFullCycle() {
        stubSuccess();
        Fixture fixture = saveReadyDelivery();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        NotificationDelivery result = processingService.process(fixture.delivery().id(), now).delivery();

        assertEquals(DeliveryStatus.SENT, result.status());
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(1, history.size());
        DeliveryAttempt attempt = history.get(0);
        assertEquals(1, attempt.attemptNumber());
        assertEquals(now, attempt.startedAt());
        assertNotNull(attempt.completedAt());
        assertTrue(!attempt.completedAt().isBefore(attempt.startedAt()));
        assertEquals(DeliveryAttemptResult.SUCCESS, attempt.result());
        assertNull(attempt.errorCode());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    // 2. Temporary failure schedules retry, second attempt succeeds.
    @Test
    void temporaryFailureRetriesThenSucceeds() {
        stubAdapter.deliveries.set(0);
        stubAdapter.delayMillis = 0;
        stubAdapter.result = DeliveryResult.temporaryFailure();
        Fixture fixture = saveReadyDelivery();
        // Backdated so the scheduled retry is already due at wall-clock time:
        // processing "now" must never be in the future.
        Instant firstNow = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(61);

        NotificationDelivery retried = processingService.process(fixture.delivery().id(), firstNow).delivery();

        assertEquals(DeliveryStatus.READY, retried.status());
        assertEquals(firstNow.plus(Duration.ofMinutes(1)), retried.nextAttemptAt());
        List<DeliveryAttempt> firstHistory = attemptHistory(fixture.delivery().id());
        assertEquals(1, firstHistory.size());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, firstHistory.get(0).result());

        stubAdapter.result = DeliveryResult.success();
        NotificationDelivery sent =
                processingService.process(fixture.delivery().id(), Instant.now()).delivery();

        assertEquals(DeliveryStatus.SENT, sent.status());
        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(2, history.size());
        assertEquals(List.of(1, 2), history.stream().map(DeliveryAttempt::attemptNumber).toList());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, history.get(0).result());
        assertEquals(DeliveryAttemptResult.SUCCESS, history.get(1).result());
        assertEquals(2, stubAdapter.deliveries.get());
    }

    // 3. Permanent failure ends delivery without retry.
    @Test
    void permanentFailureEndsDeliveryWithoutRetry() {
        stubSuccess();
        stubAdapter.result = DeliveryResult.permanentFailure();
        Fixture fixture = saveReadyDelivery();
        Instant now = Instant.now();

        NotificationDelivery result = processingService.process(fixture.delivery().id(), now).delivery();

        assertEquals(DeliveryStatus.FAILED, result.status());
        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(1, history.size());
        assertEquals(DeliveryAttemptResult.PERMANENT_FAILURE, history.get(0).result());

        NotificationDelivery repeated =
                processingService.process(fixture.delivery().id(), now.plusSeconds(3600)).delivery();

        assertEquals(DeliveryStatus.FAILED, repeated.status());
        assertEquals(1, attemptHistory(fixture.delivery().id()).size());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    // 4. Retry policy exhaustion: READY/READY/FAILED across three attempts.
    @Test
    void retryPolicyExhaustionEndsFailed() {
        stubAdapter.deliveries.set(0);
        stubAdapter.delayMillis = 0;
        stubAdapter.result = DeliveryResult.temporaryFailure();
        Fixture fixture = saveReadyDelivery();
        // Backdated processing times: each must be due (>= previous nextAttemptAt)
        // and never in the future (service stamps completion with wall-clock time).
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant firstNow = base.minusSeconds(400);

        NotificationDelivery afterFirst = processingService.process(fixture.delivery().id(), firstNow).delivery();
        assertEquals(DeliveryStatus.READY, afterFirst.status());
        assertEquals(firstNow.plus(Duration.ofMinutes(1)), afterFirst.nextAttemptAt());

        Instant secondNow = base.minusSeconds(300);
        NotificationDelivery afterSecond = processingService.process(fixture.delivery().id(), secondNow).delivery();
        assertEquals(DeliveryStatus.READY, afterSecond.status());
        assertEquals(secondNow.plus(Duration.ofMinutes(5)), afterSecond.nextAttemptAt());

        NotificationDelivery afterThird = processingService.process(fixture.delivery().id(), base).delivery();
        assertEquals(DeliveryStatus.FAILED, afterThird.status());

        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(3, history.size());
        assertEquals(List.of(1, 2, 3), history.stream().map(DeliveryAttempt::attemptNumber).toList());
        assertTrue(history.stream()
                .allMatch(attempt -> attempt.result() == DeliveryAttemptResult.TEMPORARY_FAILURE));

        NotificationDelivery repeated =
                processingService.process(fixture.delivery().id(), Instant.now()).delivery();
        assertEquals(DeliveryStatus.FAILED, repeated.status());
        assertEquals(3, attemptHistory(fixture.delivery().id()).size());
        assertEquals(3, stubAdapter.deliveries.get());
    }

    // 5. Independent deliveries for distinct channels of one notification.
    @Test
    void distinctChannelsProcessedIndependently() {
        stubSuccess();
        Fixture fixture = saveReadyDelivery();
        NotificationChannel secondChannel = saveChannel(fixture.notification());
        NotificationDelivery secondDelivery = saveDelivery(fixture.notification(), secondChannel);

        NotificationDelivery sentA = processingService.process(fixture.delivery().id(), Instant.now()).delivery();
        assertEquals(DeliveryStatus.SENT, sentA.status());

        stubAdapter.result = DeliveryResult.temporaryFailure();
        // Backdated so the scheduled retry is already due at wall-clock time.
        Instant firstNowB = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(61);
        NotificationDelivery retriedB = processingService.process(secondDelivery.id(), firstNowB).delivery();
        assertEquals(DeliveryStatus.READY, retriedB.status());
        assertNotNull(retriedB.nextAttemptAt());

        stubAdapter.result = DeliveryResult.success();
        NotificationDelivery sentB =
                processingService.process(secondDelivery.id(), Instant.now()).delivery();
        assertEquals(DeliveryStatus.SENT, sentB.status());

        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(1, attemptHistory(fixture.delivery().id()).size());
        List<DeliveryAttempt> historyB = attemptHistory(secondDelivery.id());
        assertEquals(2, historyB.size());
        assertEquals(List.of(1, 2), historyB.stream().map(DeliveryAttempt::attemptNumber).toList());
        assertEquals(fixture.delivery().notificationChannel().id(),
                attemptHistory(fixture.delivery().id()).get(0).notificationDelivery()
                        .notificationChannel().id());
    }

    // 6. Concurrent processing of one delivery: single claim, single sending, single attempt.
    @Test
    void concurrentProcessingDeliversOnlyOnce() throws Exception {
        stubSuccess();
        stubAdapter.delayMillis = 500;
        Fixture fixture = saveReadyDelivery();
        Instant now = Instant.now();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<DeliveryProcessingOutcome> first =
                    executor.submit(() -> processAfterStart(fixture.delivery().id(), now, start));
            Future<DeliveryProcessingOutcome> second =
                    executor.submit(() -> processAfterStart(fixture.delivery().id(), now, start));
            start.countDown();

            assertNotNull(first.get(30, TimeUnit.SECONDS));
            assertNotNull(second.get(30, TimeUnit.SECONDS));

            assertEquals(1, stubAdapter.deliveries.get());
            assertEquals(1, attemptHistory(fixture.delivery().id()).size());
            assertEquals(DeliveryStatus.SENT,
                    deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    // 7. Recovery + fencing race end to end through the real service.
    @Test
    void recoveryFencingRaceKeepsNewOwnerState() {
        stubAdapter.deliveries.set(0);
        stubAdapter.delayMillis = 0;
        stubAdapter.result = DeliveryResult.success();
        Fixture fixture = saveReadyDelivery();
        DeliveryClaim claimA = fencingPort.claim(fixture.delivery().id(), Instant.now()).orElseThrow();
        Instant oldStart = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claimA.delivery(), 1, oldStart);

        assertTrue(fencingPort.recoverStuck(fixture.delivery().id(), Instant.now()));
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(1, attemptHistory(fixture.delivery().id()).size());

        NotificationDelivery resultB =
                processingService.process(fixture.delivery().id(), Instant.now()).delivery();

        assertEquals(DeliveryStatus.SENT, resultB.status());
        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(2, history.size());
        assertEquals(oldStart, history.get(0).startedAt());
        assertNull(history.get(0).completedAt());
        assertNull(history.get(0).result());
        assertEquals(2, history.get(1).attemptNumber());
        assertEquals(DeliveryAttemptResult.SUCCESS, history.get(1).result());

        Optional<NotificationDelivery> stale = fencingPort.saveIfOwned(fixture.delivery().id(),
                claimA.ownershipToken(), claimA.delivery().markSent());
        assertTrue(stale.isEmpty());
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    // 8a. RetryScheduler finds a due delivery and hands it to processing.
    @Test
    void retrySchedulerProcessesDueDelivery() {
        stubSuccess();
        Fixture fixture = saveReadyDelivery();

        retryScheduler.processDueDeliveries();

        assertEquals(1, stubAdapter.deliveries.get());
        assertEquals(1, attemptHistory(fixture.delivery().id()).size());
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
    }

    // 8b. Concurrent scheduler runs still deliver only once.
    @Test
    void concurrentSchedulerRunsDeliverOnlyOnce() throws Exception {
        stubSuccess();
        stubAdapter.delayMillis = 500;
        Fixture fixture = saveReadyDelivery();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> runSchedulerAfterStart(start));
            Future<?> second = executor.submit(() -> runSchedulerAfterStart(start));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertEquals(1, stubAdapter.deliveries.get());
            assertEquals(1, attemptHistory(fixture.delivery().id()).size());
            assertEquals(DeliveryStatus.SENT,
                    deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    // 9. RecoveryScheduler restores stuck delivery, retry flow redelivers it.
    @Test
    void recoverySchedulerRestoresStuckDeliveryForRetry() {
        stubAdapter.deliveries.set(0);
        stubAdapter.delayMillis = 0;
        stubAdapter.result = DeliveryResult.success();
        Fixture fixture = saveReadyDelivery();
        DeliveryClaim claim = fencingPort.claim(fixture.delivery().id(), Instant.now()).orElseThrow();
        Instant oldStart = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, oldStart);

        recoveryScheduler.recoverStuckDeliveries();

        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(1, attemptHistory(fixture.delivery().id()).size());
        assertEquals(0, stubAdapter.deliveries.get());

        retryScheduler.processDueDeliveries();

        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        List<DeliveryAttempt> history = attemptHistory(fixture.delivery().id());
        assertEquals(2, history.size());
        assertEquals(oldStart, history.get(0).startedAt());
        assertNull(history.get(0).completedAt());
        assertEquals(2, history.get(1).attemptNumber());
        assertEquals(DeliveryAttemptResult.SUCCESS, history.get(1).result());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    private void stubSuccess() {
        stubAdapter.deliveries.set(0);
        stubAdapter.delayMillis = 0;
        stubAdapter.result = DeliveryResult.success();
    }

    private DeliveryProcessingOutcome processAfterStart(UUID id, Instant now, CountDownLatch start)
            throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return processingService.process(id, now);
    }

    private boolean runSchedulerAfterStart(CountDownLatch start) throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        retryScheduler.processDueDeliveries();
        return true;
    }

    private List<DeliveryAttempt> attemptHistory(UUID deliveryId) {
        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(deliveryId);
        createdAttemptIds.addAll(history.stream().map(DeliveryAttempt::id)
                .filter(id -> !createdAttemptIds.contains(id)).toList());
        return history;
    }

    private DeliveryAttempt saveStartedAttempt(NotificationDelivery delivery, int number, Instant startedAt) {
        DeliveryAttempt saved = attemptPort.save(
                DeliveryAttempt.started(UUID.randomUUID(), delivery, number, startedAt));
        createdAttemptIds.add(saved.id());
        return saved;
    }

    private record Fixture(Notification notification, NotificationDelivery delivery) {
    }

    private Fixture saveReadyDelivery() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "retry-it-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = saveChannel(user);
        Notification notification = saveNotification(user);
        NotificationDelivery delivery = saveDelivery(notification, channel);
        return new Fixture(notification, delivery);
    }

    private NotificationChannel saveChannel(User user) {
        return channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "test-retry", "retry-it-" + UUID.randomUUID() + "@example.com", true));
    }

    private NotificationChannel saveChannel(Notification notification) {
        return saveChannel(notification.subscription().user());
    }

    private NotificationDelivery saveDelivery(Notification notification, NotificationChannel channel) {
        NotificationDelivery delivery = deliveryPort.save(
                NotificationDelivery.of(UUID.randomUUID(), notification, channel));
        createdDeliveryIds.add(delivery.id());
        return delivery;
    }

    private Notification saveNotification(User user) {
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "retry-it-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "RetryItCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "RetryItStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "retry-it-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "retry it reason", "АКТИВНО", Set.of(poa)));
        return notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "retry it message", NotificationStatus.PENDING));
    }
}
