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
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
class NotificationClaimConcurrencyTest {

    @Autowired
    private NotificationPort notificationPort;

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
    void onlyOneConcurrentClaimSucceeds() throws Exception {
        Notification notification = savePendingNotification();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Optional<Notification>> first =
                    executor.submit(() -> claimAfterStart(notification.id(), start));
            Future<Optional<Notification>> second =
                    executor.submit(() -> claimAfterStart(notification.id(), start));
            start.countDown();

            Optional<Notification> firstResult = first.get(15, TimeUnit.SECONDS);
            Optional<Notification> secondResult = second.get(15, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream().filter(Optional::isPresent).count();
            assertEquals(1, successes);
            Notification claimed = firstResult.orElseGet(secondResult::orElseThrow);
            assertEquals(NotificationStatus.PROCESSING, claimed.status());
            assertEquals(notification.id(), claimed.id());

            Optional<Notification> current = notificationPort.findById(notification.id());
            assertTrue(current.isPresent());
            assertEquals(NotificationStatus.PROCESSING, current.get().status());

            assertTrue(notificationPort.claimForProcessing(notification.id()).isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<Notification> claimAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return notificationPort.claimForProcessing(id);
    }

    private Notification savePendingNotification() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "claim-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
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
        return notificationPort.save(Notification.of(UUID.randomUUID(), subscription, outage,
                "claim message", NotificationStatus.PENDING));
    }
}
