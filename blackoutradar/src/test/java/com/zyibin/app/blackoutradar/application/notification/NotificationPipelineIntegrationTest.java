package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * End-to-end pipeline checks through the real NotificationEngine,
 * RetryProcessingService, finalization and schedulers against PostgreSQL.
 * Only the external DeliveryPort is stubbed.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, NotificationPipelineIntegrationTest.StubDeliveryConfiguration.class})
class NotificationPipelineIntegrationTest {

    static class StubAdapter implements DeliveryPort {
        final AtomicInteger deliveries = new AtomicInteger();
        final List<DeliveredCall> calls = new ArrayList<>();
        volatile DeliveryResult result = DeliveryResult.success();

        @Override
        public String channelType() {
            return "test-pipeline";
        }

        @Override
        public synchronized DeliveryResult deliver(NotificationChannel channel, String message) {
            deliveries.incrementAndGet();
            calls.add(new DeliveredCall(channel, message));
            return result;
        }
    }

    record DeliveredCall(NotificationChannel channel, String message) {
    }

    @TestConfiguration
    static class StubDeliveryConfiguration {
        @Bean
        StubAdapter stubAdapter() {
            return new StubAdapter();
        }
    }

    private static final String MESSAGE = "pipeline integration message";

    @Autowired
    private NotificationEngine engine;

    @Autowired
    private RetryProcessingService processingService;

    @Autowired
    private NotificationDeliveryFencingPort fencingPort;

    @Autowired
    private NotificationPort notificationPort;

    @Autowired
    private NotificationDeliveryPort deliveryPort;

    @Autowired
    private DeliveryAttemptPort attemptPort;

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

    @Test
    void singleDeliveryFinalizesSent() {
        stubSuccess();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline"));

        Notification result = engine.process(fixture.notification().id());

        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals(MESSAGE, result.message());
        List<NotificationDelivery> deliveries = deliveriesOf(fixture.notification().id());
        assertEquals(1, deliveries.size());
        assertEquals(DeliveryStatus.SENT, deliveries.get(0).status());
        List<DeliveryAttempt> history = attemptsOf(deliveries.get(0).id());
        assertEquals(1, history.size());
        assertEquals(DeliveryAttemptResult.SUCCESS, history.get(0).result());
        assertEquals(1, stubAdapter.deliveries.get());
        assertEquals(MESSAGE, stubAdapter.calls.get(0).message());
        assertEquals(deliveries.get(0).notificationChannel().id(),
                stubAdapter.calls.get(0).channel().id());
    }

    @Test
    void allSentDeliveriesFinalizeSent() {
        stubSuccess();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline", "test-pipeline"));

        Notification result = engine.process(fixture.notification().id());

        assertEquals(NotificationStatus.SENT, result.status());
        List<NotificationDelivery> deliveries = deliveriesOf(fixture.notification().id());
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().allMatch(delivery -> delivery.status() == DeliveryStatus.SENT));
        assertEquals(2, stubAdapter.deliveries.get());
        for (NotificationDelivery delivery : deliveries) {
            assertEquals(1, attemptsOf(delivery.id()).size());
        }
    }

    @Test
    void terminalMixWithFailedFinalizesFailed() {
        stubSuccess();
        Fixture fixture =
                savePendingNotificationWithChannels(List.of("test-pipeline", "unregistered-channel"));

        Notification result = engine.process(fixture.notification().id());

        assertEquals(NotificationStatus.FAILED, result.status());
        List<NotificationDelivery> deliveries = deliveriesOf(fixture.notification().id());
        assertEquals(2, deliveries.size());
        assertEquals(1, deliveries.stream()
                .filter(delivery -> delivery.status() == DeliveryStatus.SENT).count());
        assertEquals(1, deliveries.stream()
                .filter(delivery -> delivery.status() == DeliveryStatus.FAILED).count());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    @Test
    void pendingRetryKeepsNotificationProcessing() {
        stubAdapter.deliveries.set(0);
        stubAdapter.result = DeliveryResult.temporaryFailure();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline"));

        Notification result = engine.process(fixture.notification().id());

        assertEquals(NotificationStatus.PROCESSING, result.status());
        List<NotificationDelivery> deliveries = deliveriesOf(fixture.notification().id());
        assertEquals(1, deliveries.size());
        assertEquals(DeliveryStatus.READY, deliveries.get(0).status());
        assertNotNull(deliveries.get(0).nextAttemptAt());
        List<DeliveryAttempt> history = attemptsOf(deliveries.get(0).id());
        assertEquals(1, history.size());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, history.get(0).result());
    }

    @Test
    void parallelCompletionFinalizesSent() throws Exception {
        stubSuccess();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline", "test-pipeline"));
        Notification claimed = notificationPort.claimForProcessing(fixture.notification().id()).orElseThrow();
        NotificationDelivery first = saveDelivery(claimed, saveChannel(claimed, "test-pipeline"));
        NotificationDelivery second = saveDelivery(claimed, saveChannel(claimed, "test-pipeline"));
        Instant now = Instant.now();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<DeliveryProcessingOutcome> firstDone =
                    executor.submit(() -> processAfterStart(first.id(), now, start));
            Future<DeliveryProcessingOutcome> secondDone =
                    executor.submit(() -> processAfterStart(second.id(), now, start));
            start.countDown();

            assertTrue(firstDone.get(30, TimeUnit.SECONDS).fencedCompletion());
            assertTrue(secondDone.get(30, TimeUnit.SECONDS).fencedCompletion());

            assertEquals(NotificationStatus.SENT,
                    notificationPort.findById(fixture.notification().id()).orElseThrow().status());
            assertEquals(DeliveryStatus.SENT,
                    deliveryPort.findById(first.id()).orElseThrow().status());
            assertEquals(DeliveryStatus.SENT,
                    deliveryPort.findById(second.id()).orElseThrow().status());
            assertEquals(2, stubAdapter.deliveries.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleWorkerCompletionDoesNotFinalizeAwayNewState() {
        stubSuccess();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline"));
        Notification claimed = notificationPort.claimForProcessing(fixture.notification().id()).orElseThrow();
        NotificationDelivery created = saveDelivery(claimed, fixture.channels().get(0));
        DeliveryClaim claimA = fencingPort.claim(created.id(), Instant.now()).orElseThrow();
        Instant oldStart = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        saveStartedAttempt(claimA.delivery(), 1, oldStart);

        assertTrue(fencingPort.recoverStuck(created.id(), Instant.now()));

        DeliveryProcessingOutcome outcomeB =
                processingService.process(created.id(), Instant.now());

        assertTrue(outcomeB.fencedCompletion());
        assertEquals(NotificationStatus.SENT,
                notificationPort.findById(fixture.notification().id()).orElseThrow().status());

        Optional<NotificationDelivery> stale = fencingPort.saveIfOwned(created.id(),
                claimA.ownershipToken(), claimA.delivery().markSent());

        assertTrue(stale.isEmpty());
        assertEquals(NotificationStatus.SENT,
                notificationPort.findById(fixture.notification().id()).orElseThrow().status());
        List<DeliveryAttempt> history = attemptsOf(created.id());
        assertEquals(2, history.size());
        assertNull(history.get(0).completedAt());
    }

    @Test
    void lostNotificationClaimReturnsCurrentState() {
        stubSuccess();
        Fixture fixture = savePendingNotificationWithChannels(List.of("test-pipeline"));
        Notification claimed = notificationPort.claimForProcessing(fixture.notification().id()).orElseThrow();

        Notification result = engine.process(fixture.notification().id());

        assertEquals(claimed.id(), result.id());
        assertEquals(0, stubAdapter.deliveries.get());
        assertTrue(deliveriesOf(fixture.notification().id()).isEmpty());
    }

    private DeliveryProcessingOutcome processAfterStart(UUID id, Instant now, CountDownLatch start)
            throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return processingService.process(id, now);
    }

    private List<NotificationDelivery> deliveriesOf(UUID notificationId) {
        return deliveryPort.findByNotificationId(notificationId).stream()
                .peek(delivery -> {
                    if (!createdDeliveryIds.contains(delivery.id())) {
                        createdDeliveryIds.add(delivery.id());
                    }
                })
                .toList();
    }

    private List<DeliveryAttempt> attemptsOf(UUID deliveryId) {
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

    private record Fixture(Notification notification, List<NotificationChannel> channels) {
    }

    private Fixture savePendingNotificationWithChannels(List<String> channelTypes) {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "pipeline-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        Notification notification = saveNotification(user);
        List<NotificationChannel> channels = channelTypes.stream()
                .map(type -> saveChannel(notification, type))
                .toList();
        return new Fixture(notification, channels);
    }

    private NotificationChannel saveChannel(Notification notification, String type) {
        return channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                notification.subscription().user(), type,
                "pipeline-" + UUID.randomUUID() + "@example.com", true));
    }

    private NotificationDelivery saveDelivery(Notification notification, NotificationChannel channel) {
        NotificationDelivery delivery = deliveryPort.save(
                NotificationDelivery.of(UUID.randomUUID(), notification, channel));
        createdDeliveryIds.add(delivery.id());
        return delivery;
    }

    private Notification saveNotification(User user) {
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "pipeline-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "PipelineCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "PipelineStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "pipeline-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "pipeline reason", "АКТИВНО", Set.of(poa)));
        return notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, MESSAGE, NotificationStatus.PENDING));
    }

    private void stubSuccess() {
        stubAdapter.deliveries.set(0);
        stubAdapter.calls.clear();
        stubAdapter.result = DeliveryResult.success();
    }
}
