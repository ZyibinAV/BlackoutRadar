package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.notification.DeliveryClaim;
import com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationDeliveryFencingTest {

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

    private final List<UUID> createdDeliveryIds = new ArrayList<>();
    private final List<UUID> createdAttemptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAllById(createdAttemptIds);
        deliveryRepository.deleteAllById(createdDeliveryIds);
        createdAttemptIds.clear();
        createdDeliveryIds.clear();
    }

    // A. Atomic claim: exactly one success, winner gets token.
    @Test
    void concurrentClaimsGrantExactlyOneToken() throws Exception {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Optional<DeliveryClaim>> first = executor.submit(() -> claimAfterStart(delivery.id(), start));
            Future<Optional<DeliveryClaim>> second = executor.submit(() -> claimAfterStart(delivery.id(), start));
            start.countDown();

            Optional<DeliveryClaim> firstResult = first.get(15, TimeUnit.SECONDS);
            Optional<DeliveryClaim> secondResult = second.get(15, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream().filter(Optional::isPresent).count();
            assertEquals(1, successes);
            DeliveryClaim winner = firstResult.orElseGet(() -> secondResult.orElseThrow());
            assertNotNull(winner.ownershipToken());
            assertEquals(DeliveryStatus.PROCESSING, winner.delivery().status());
            assertNotNull(deliveryRepository.findById(delivery.id()).orElseThrow().getProcessingToken());
            assertEquals(DeliveryStatus.PROCESSING,
                    deliveryPort.findById(delivery.id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulClaimIssuesUniqueTokensPerRound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim first = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        assertNotNull(first.ownershipToken());
        assertEquals(DeliveryStatus.PROCESSING, first.delivery().status());

        // Second claim must fail while PROCESSING.
        assertTrue(fencingPort.claim(delivery.id(), Instant.now()).isEmpty());
    }

    // B. Recovery: PROCESSING + old incomplete -> READY, ownership cleared.
    @Test
    void recoveryMovesStuckToReadyAndClearsOwnership() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, old);
        assertNotNull(deliveryRepository.findById(delivery.id()).orElseThrow().getProcessingToken());

        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
        assertNull(deliveryRepository.findById(delivery.id()).orElseThrow().getProcessingToken());
    }

    // C. Recovery not applied.
    @Test
    void recoveryNotAppliedToReady() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(delivery, 1, old);

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now()));
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedToSent() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.SENT, null);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(delivery, 1, old);

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now()));
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedToFailed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.FAILED, null);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(delivery, 1, old);

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now()));
        assertEquals(DeliveryStatus.FAILED,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedWhenOnlyCompletedAttempts() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveCompletedAttempt(claim.delivery(), 1, old, DeliveryAttemptResult.SUCCESS);

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now()));
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedWithoutAttempts() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now()));
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedToFreshIncompleteAttempt() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant fresh = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, fresh);

        assertFalse(fencingPort.recoverStuck(delivery.id(), Instant.now().minus(1, ChronoUnit.HOURS)));
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedWhenStartedAtEqualsThreshold() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant threshold = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, threshold);

        assertFalse(fencingPort.recoverStuck(delivery.id(), threshold));
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void recoveryNotAppliedWhenNewerIncompleteAttemptIsFresh() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim first = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant threshold = base.minus(1, ChronoUnit.HOURS);
        saveStartedAttempt(first.delivery(), 1, base.minus(2, ChronoUnit.HOURS));

        assertTrue(fencingPort.recoverStuck(delivery.id(), threshold));

        DeliveryClaim second = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        saveStartedAttempt(second.delivery(), 2, base.minus(5, ChronoUnit.MINUTES));

        assertFalse(fencingPort.recoverStuck(delivery.id(), threshold));
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    // D. Old owner cannot mark SENT after recovery.
    @Test
    void oldOwnerCannotMarkSentAfterRecovery() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        UUID tokenA = claim.ownershipToken();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, old);

        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        NotificationDelivery processingView = claim.delivery();
        Optional<NotificationDelivery> staleWrite =
                fencingPort.saveIfOwned(delivery.id(), tokenA, processingView.markSent());

        assertTrue(staleWrite.isEmpty());
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void oldOwnerCannotScheduleRetryAfterRecovery() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        UUID tokenA = claim.ownershipToken();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, old);

        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        Optional<NotificationDelivery> staleWrite = fencingPort.saveIfOwned(delivery.id(), tokenA,
                claim.delivery().scheduleRetry(Instant.now().plus(5, ChronoUnit.MINUTES)));

        assertTrue(staleWrite.isEmpty());
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    // E. New owner after recovery can complete.
    @Test
    void newOwnerCanCompleteAfterRecovery() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim first = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(first.delivery(), 1, old);

        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        DeliveryClaim second = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        assertNotNull(second.ownershipToken());
        assertNotEquals(first.ownershipToken(), second.ownershipToken());

        Optional<NotificationDelivery> completed =
                fencingPort.saveIfOwned(delivery.id(), second.ownershipToken(),
                        second.delivery().markSent());

        assertTrue(completed.isPresent());
        assertEquals(DeliveryStatus.SENT, completed.get().status());
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    // F. Two parallel recoveries: exactly one succeeds.
    @Test
    void concurrentRecoveriesGrantExactlyOneSuccess() throws Exception {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claim.delivery(), 1, old);
        Instant threshold = Instant.now();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = executor.submit(() -> recoverAfterStart(delivery.id(), threshold, start));
            Future<Boolean> second = executor.submit(() -> recoverAfterStart(delivery.id(), threshold, start));
            start.countDown();

            boolean firstResult = first.get(15, TimeUnit.SECONDS);
            boolean secondResult = second.get(15, TimeUnit.SECONDS);

            assertNotEquals(firstResult, secondResult);
            assertTrue(firstResult || secondResult);
            assertEquals(DeliveryStatus.READY,
                    deliveryPort.findById(delivery.id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    // G. Worker/recovery race: old worker cannot change state.
    @Test
    void workerRaceWithRecoveryKeepsFencing() {
        // Worker A claims with token-A and starts attempt #1.
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim workerA = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(workerA.delivery(), 1, old);

        // Recovery invalidates token-A.
        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        // Worker B claims with token-B and starts attempt #2.
        DeliveryClaim workerB = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        assertNotEquals(workerA.ownershipToken(), workerB.ownershipToken());
        saveStartedAttempt(workerB.delivery(), 2, Instant.now().truncatedTo(ChronoUnit.MILLIS));

        // Worker A finishes external call late and tries SUCCESS: must be rejected.
        Optional<NotificationDelivery> staleSuccess = fencingPort.saveIfOwned(delivery.id(),
                workerA.ownershipToken(), workerA.delivery().markSent());
        assertTrue(staleSuccess.isEmpty());

        // State must still be owned by B (PROCESSING), not SENT.
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());

        // Worker B can still complete.
        Optional<NotificationDelivery> freshSuccess = fencingPort.saveIfOwned(delivery.id(),
                workerB.ownershipToken(), workerB.delivery().markSent());
        assertTrue(freshSuccess.isPresent());
        assertEquals(DeliveryStatus.SENT, freshSuccess.get().status());
    }

    // H. Attempt history preserved; recovery creates nothing.
    @Test
    void recoveryPreservesIncompleteAttemptAndCreatesNothing() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        DeliveryClaim claim = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        DeliveryAttempt first = saveStartedAttempt(claim.delivery(), 1, old);
        int attemptsBefore = attemptPort.findByNotificationDeliveryId(delivery.id()).size();

        assertTrue(fencingPort.recoverStuck(delivery.id(), Instant.now()));

        List<DeliveryAttempt> afterRecovery = attemptPort.findByNotificationDeliveryId(delivery.id());
        assertEquals(attemptsBefore, afterRecovery.size());
        assertEquals(1, afterRecovery.size());
        assertEquals(first.id(), afterRecovery.get(0).id());
        assertTrue(afterRecovery.stream().noneMatch(DeliveryAttempt::isCompleted));

        // New processing creates attempt #2 separately; recovery itself created nothing.
        DeliveryClaim next = fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        DeliveryAttempt second = saveStartedAttempt(next.delivery(), 2,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(delivery.id());
        assertEquals(2, history.size());
        assertEquals(List.of(1, 2), history.stream().map(DeliveryAttempt::attemptNumber).toList());
        assertEquals(first.id(), history.get(0).id());
        assertEquals(second.id(), history.get(1).id());
    }

    @Test
    void processingTokenNullableForTerminalStatesAndPresentForProcessing() {
        NotificationDelivery ready = saveDelivery(DeliveryStatus.READY, null);
        assertNull(deliveryRepository.findById(ready.id()).orElseThrow().getProcessingToken());

        DeliveryClaim claim = fencingPort.claim(ready.id(), Instant.now()).orElseThrow();
        assertNotNull(deliveryRepository.findById(ready.id()).orElseThrow().getProcessingToken());

        fencingPort.saveIfOwned(ready.id(), claim.ownershipToken(), claim.delivery().markSent());
        assertNull(deliveryRepository.findById(ready.id()).orElseThrow().getProcessingToken());
    }

    private Optional<DeliveryClaim> claimAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return fencingPort.claim(id, Instant.now());
    }

    private boolean recoverAfterStart(UUID id, Instant threshold, CountDownLatch start) throws Exception {
        if (!start.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return fencingPort.recoverStuck(id, threshold);
    }

    private DeliveryAttempt saveStartedAttempt(NotificationDelivery delivery, int number, Instant startedAt) {
        DeliveryAttempt saved = attemptPort.save(
                DeliveryAttempt.started(UUID.randomUUID(), delivery, number, startedAt));
        createdAttemptIds.add(saved.id());
        return saved;
    }

    private void saveCompletedAttempt(NotificationDelivery delivery, int number, Instant startedAt,
                                      DeliveryAttemptResult result) {
        DeliveryAttempt saved = attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery,
                number, startedAt, startedAt.plus(2, ChronoUnit.SECONDS), result, null));
        createdAttemptIds.add(saved.id());
    }

    private NotificationDelivery saveDelivery(DeliveryStatus status, Instant nextAttemptAt) {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "fencing-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "fencing-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "FencingCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "FencingStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "fencing-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "fencing reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "fencing message", NotificationStatus.PENDING));
        NotificationDelivery delivery = deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(),
                notification, channel, status, nextAttemptAt));
        createdDeliveryIds.add(delivery.id());
        return delivery;
    }
}
