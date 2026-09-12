package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationDeliveryClaimTest {

    @Autowired
    private NotificationDeliveryPort deliveryPort;

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
    void cleanUpDeliveries() {
        attemptRepository.deleteAllById(createdAttemptIds);
        deliveryRepository.deleteAllById(createdDeliveryIds);
        createdAttemptIds.clear();
        createdDeliveryIds.clear();
    }

    @Test
    void readyWithPastNextAttemptAtIsClaimed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY,
                Instant.now().minus(1, ChronoUnit.HOURS));

        Optional<NotificationDelivery> claimed =
                deliveryPort.claimForProcessing(delivery.id(), Instant.now());

        assertTrue(claimed.isPresent());
        assertEquals(DeliveryStatus.PROCESSING, claimed.get().status());
        assertEquals(delivery.id(), claimed.get().id());
    }

    @Test
    void readyWithNullNextAttemptAtIsClaimedImmediately() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);

        Optional<NotificationDelivery> claimed =
                deliveryPort.claimForProcessing(delivery.id(), Instant.now());

        assertTrue(claimed.isPresent());
        assertEquals(DeliveryStatus.PROCESSING, claimed.get().status());
    }

    @Test
    void readyWithNextAttemptAtEqualToNowIsClaimed() {
        Instant now = Instant.now();
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, now);

        Optional<NotificationDelivery> claimed = deliveryPort.claimForProcessing(delivery.id(), now);

        assertTrue(claimed.isPresent());
        assertEquals(DeliveryStatus.PROCESSING, claimed.get().status());
    }

    @Test
    @Transactional
    void claimedDeliveryIsProcessingEvenWhenEntityWasPreloaded() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);

        Optional<NotificationDelivery> preloaded = deliveryPort.findById(delivery.id());
        assertTrue(preloaded.isPresent());
        assertEquals(DeliveryStatus.READY, preloaded.get().status());

        Optional<NotificationDelivery> claimed =
                deliveryPort.claimForProcessing(delivery.id(), Instant.now());

        assertTrue(claimed.isPresent());
        assertEquals(DeliveryStatus.PROCESSING, claimed.get().status());
    }

    @Test
    void readyWithFutureNextAttemptAtIsNotClaimed() {        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY,
                Instant.now().plus(1, ChronoUnit.HOURS));

        Optional<NotificationDelivery> claimed =
                deliveryPort.claimForProcessing(delivery.id(), Instant.now());

        assertTrue(claimed.isEmpty());
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void processingIsNotClaimed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING, null);

        assertTrue(deliveryPort.claimForProcessing(delivery.id(), Instant.now()).isEmpty());
    }

    @Test
    void sentIsNotClaimed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.SENT, null);

        assertTrue(deliveryPort.claimForProcessing(delivery.id(), Instant.now()).isEmpty());
    }

    @Test
    void failedIsNotClaimed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.FAILED, null);

        assertTrue(deliveryPort.claimForProcessing(delivery.id(), Instant.now()).isEmpty());
    }

    @Test
    void missingDeliveryIsNotClaimed() {
        assertTrue(deliveryPort.claimForProcessing(UUID.randomUUID(), Instant.now()).isEmpty());
    }

    @Test
    void repeatedClaimIsNotClaimed() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);

        assertTrue(deliveryPort.claimForProcessing(delivery.id(), Instant.now()).isPresent());
        assertTrue(deliveryPort.claimForProcessing(delivery.id(), Instant.now()).isEmpty());
        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void concurrentClaimsGrantExactlyOneSuccess() throws Exception {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Optional<NotificationDelivery>> first =
                    executor.submit(() -> claimAfterStart(delivery.id(), start));
            Future<Optional<NotificationDelivery>> second =
                    executor.submit(() -> claimAfterStart(delivery.id(), start));
            start.countDown();

            Optional<NotificationDelivery> firstResult = first.get(15, TimeUnit.SECONDS);
            Optional<NotificationDelivery> secondResult = second.get(15, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream().filter(Optional::isPresent).count();
            assertEquals(1, successes);
            assertEquals(DeliveryStatus.PROCESSING,
                    deliveryPort.findById(delivery.id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void attemptCreatedAfterClaimWithNextNumber() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY, null);
        Instant now = Instant.now();

        assertEquals(1, attemptPort.nextAttemptNumber(delivery.id()));

        NotificationDelivery claimed = deliveryPort.claimForProcessing(delivery.id(), now).orElseThrow();
        int number = attemptPort.nextAttemptNumber(claimed.id());
        DeliveryAttempt attempt = DeliveryAttempt.started(UUID.randomUUID(), claimed, number,
                now.truncatedTo(ChronoUnit.MILLIS));

        DeliveryAttempt saved = attemptPort.save(attempt);

        createdAttemptIds.add(saved.id());
        assertEquals(1, saved.attemptNumber());
        assertEquals(claimed.id(), saved.notificationDelivery().id());
        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(claimed.id());
        assertEquals(1, history.size());
        assertEquals(2, attemptPort.nextAttemptNumber(claimed.id()));
    }

    @Test
    void nextAttemptNumberIndependentPerDelivery() {
        NotificationDelivery first = saveDelivery(DeliveryStatus.READY, null);
        NotificationDelivery second = saveDelivery(DeliveryStatus.READY, null);
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        createdAttemptIds.add(attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), first, 1, startedAt)).id());

        assertEquals(2, attemptPort.nextAttemptNumber(first.id()));
        assertEquals(1, attemptPort.nextAttemptNumber(second.id()));
    }

    private Optional<NotificationDelivery> claimAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return deliveryPort.claimForProcessing(id, Instant.now());
    }

    private NotificationDelivery saveDelivery(DeliveryStatus status, Instant nextAttemptAt) {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "claim-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "claim-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "ClaimCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "ClaimStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "claim-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "claim reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "claim message", NotificationStatus.PENDING));
        NotificationDelivery delivery = deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(),
                notification, channel, status, nextAttemptAt));
        createdDeliveryIds.add(delivery.id());
        return delivery;
    }
}
