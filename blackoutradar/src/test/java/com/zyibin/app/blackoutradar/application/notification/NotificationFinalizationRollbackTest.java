package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.zyibin.app.blackoutradar.persistence.jpa.adapter.NotificationPersistenceAdapter;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.DeliveryAttemptJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationDeliveryJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Proves that the fenced delivery update and the notification finalization
 * commit or roll back together: no terminal delivery may stay committed while
 * its mandatory finalization was not.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, NotificationFinalizationRollbackTest.StubDeliveryConfiguration.class,
        NotificationFinalizationRollbackTest.FailingSaveConfiguration.class})
class NotificationFinalizationRollbackTest {

    static class StubAdapter implements DeliveryPort {
        final AtomicInteger deliveries = new AtomicInteger();
        volatile DeliveryResult result = DeliveryResult.success();

        @Override
        public String channelType() {
            return "test-rollback";
        }

        @Override
        public DeliveryResult deliver(NotificationChannel channel, String message) {
            deliveries.incrementAndGet();
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

    static class FailFlag {
        final AtomicBoolean fail = new AtomicBoolean(false);
    }

    @TestConfiguration
    static class FailingSaveConfiguration {
        @Bean
        FailFlag failFlag() {
            return new FailFlag();
        }

        @Bean
        @Primary
        NotificationPort failingNotificationPort(NotificationPersistenceAdapter delegate, FailFlag flag) {
            return new NotificationPort() {
                @Override
                public Optional<Notification> findById(UUID id) {
                    return delegate.findById(id);
                }

                @Override
                public Optional<Notification> findBySubscriptionAndPowerOutage(UUID subscriptionId,
                                                                               UUID powerOutageId) {
                    return delegate.findBySubscriptionAndPowerOutage(subscriptionId, powerOutageId);
                }

                @Override
                public Notification save(Notification notification) {
                    if (flag.fail.get()) {
                        throw new RuntimeException("simulated finalization failure");
                    }
                    return delegate.save(notification);
                }

                @Override
                public com.zyibin.app.blackoutradar.domain.notification.NotificationCreation findOrCreate(
                        Notification notification) {
                    return delegate.findOrCreate(notification);
                }

                @Override
                public Optional<Notification> claimForProcessing(UUID id) {
                    return delegate.claimForProcessing(id);
                }

                @Override
                public Optional<Notification> lockById(UUID id) {
                    return delegate.lockById(id);
                }
            };
        }
    }

    @Autowired
    private RetryProcessingService processingService;

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

    @Autowired
    private FailFlag failFlag;

    private final List<UUID> createdDeliveryIds = new ArrayList<>();
    private final List<UUID> createdAttemptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        failFlag.fail.set(false);
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
    void failedFinalizationRollsBackFencedUpdate() {
        stubAdapter.deliveries.set(0);
        stubAdapter.result = DeliveryResult.success();
        Fixture fixture = saveReadyDelivery();
        failFlag.fail.set(true);

        assertThrows(RuntimeException.class,
                () -> processingService.process(fixture.delivery().id(), Instant.now()));

        assertEquals(DeliveryStatus.PROCESSING,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(NotificationStatus.PENDING,
                notificationPort.findById(fixture.notification().id()).orElseThrow().status());
        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(fixture.delivery().id());
        createdAttemptIds.addAll(history.stream().map(DeliveryAttempt::id).toList());
        assertEquals(1, history.size());
        assertEquals(DeliveryAttemptResult.SUCCESS, history.get(0).result());
        assertEquals(1, stubAdapter.deliveries.get());
    }

    @Test
    void successfulCompletionCommitsDeliveryAndFinalizationTogether() {
        stubAdapter.deliveries.set(0);
        stubAdapter.result = DeliveryResult.success();
        Fixture fixture = saveReadyDelivery();

        DeliveryProcessingOutcome outcome =
                processingService.process(fixture.delivery().id(), Instant.now());

        assertTrue(outcome.fencedCompletion());
        assertEquals(DeliveryStatus.SENT, outcome.delivery().status());
        assertEquals(DeliveryStatus.SENT,
                deliveryPort.findById(fixture.delivery().id()).orElseThrow().status());
        assertEquals(NotificationStatus.SENT,
                notificationPort.findById(fixture.notification().id()).orElseThrow().status());
    }

    private record Fixture(Notification notification, NotificationDelivery delivery) {
    }

    private Fixture saveReadyDelivery() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "rollback-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "test-rollback", "rollback-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "rollback-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "RollbackCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "RollbackStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "rollback-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "rollback reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "rollback message", NotificationStatus.PENDING));
        NotificationDelivery delivery = deliveryPort.save(
                NotificationDelivery.of(UUID.randomUUID(), notification, channel));
        createdDeliveryIds.add(delivery.id());
        return new Fixture(notification, delivery);
    }
}
