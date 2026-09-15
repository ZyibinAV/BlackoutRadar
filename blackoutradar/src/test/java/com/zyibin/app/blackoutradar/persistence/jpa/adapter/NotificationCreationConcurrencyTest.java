package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationCreation;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationCreationConcurrencyTest {

    @Autowired
    private NotificationPort notificationPort;

    @Autowired
    private NotificationJpaRepository notificationRepository;

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

    @Test
    void concurrentCreationReturnsSingleCanonicalNotification() throws Exception {
        Fixture fixture = saveFixture();
        long rowsBefore = notificationRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<NotificationCreation> first = executor.submit(() ->
                    createAfterStart(fixture, start));
            Future<NotificationCreation> second = executor.submit(() ->
                    createAfterStart(fixture, start));
            start.countDown();

            NotificationCreation firstResult = first.get(30, TimeUnit.SECONDS);
            NotificationCreation secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(firstResult.notification().id(), secondResult.notification().id());
            assertEquals(NotificationStatus.PENDING, firstResult.notification().status());
            assertEquals(fixture.message(), firstResult.notification().message());
            assertTrue(firstResult.created() ^ secondResult.created());
            assertEquals(rowsBefore + 1, notificationRepository.count());
            assertTrue(notificationPort
                    .findBySubscriptionAndPowerOutage(
                            fixture.subscription().id(), fixture.outage().id())
                    .isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void secondCallReturnsExistingWithoutCreating() {
        Fixture fixture = saveFixture();

        NotificationCreation first = notificationPort.findOrCreate(
                Notification.of(UUID.randomUUID(), fixture.subscription(), fixture.outage(),
                        fixture.message()));
        NotificationCreation second = notificationPort.findOrCreate(
                Notification.of(UUID.randomUUID(), fixture.subscription(), fixture.outage(),
                        fixture.message()));

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.notification().id(), second.notification().id());
    }

    private NotificationCreation createAfterStart(Fixture fixture, CountDownLatch start) throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return notificationPort.findOrCreate(Notification.of(UUID.randomUUID(),
                fixture.subscription(), fixture.outage(), fixture.message()));
    }

    private record Fixture(Subscription subscription, PowerOutage outage, String message) {
    }

    private Fixture saveFixture() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "creation-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "creation-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "CreationCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "CreationStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "creation-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "creation reason", "АКТИВНО", Set.of(poa)));
        return new Fixture(subscription, outage, "creation message");
    }
}
