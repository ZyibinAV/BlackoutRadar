package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, NotificationEngineConcurrencyTest.CountingDeliveryConfiguration.class})
class NotificationEngineConcurrencyTest {

    static class CountingAdapter implements DeliveryPort {
        final AtomicInteger deliveries = new AtomicInteger();

        @Override
        public String channelType() {
            return "test-claim";
        }

        @Override
        public DeliveryResult deliver(NotificationChannel channel, String message) {
            deliveries.incrementAndGet();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return DeliveryResult.success();
        }
    }

    @TestConfiguration
    static class CountingDeliveryConfiguration {
        @Bean
        CountingAdapter countingAdapter() {
            return new CountingAdapter();
        }
    }

    @Autowired
    private NotificationEngine engine;

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
    private CountingAdapter countingAdapter;

    @Test
    void concurrentProcessDeliversOnlyOnce() throws Exception {
        Notification notification = savePendingNotificationWithChannel();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Notification> first =
                    executor.submit(() -> processAfterStart(notification.id(), start));
            Future<Notification> second =
                    executor.submit(() -> processAfterStart(notification.id(), start));
            start.countDown();

            Notification firstResult = first.get(30, TimeUnit.SECONDS);
            Notification secondResult = second.get(30, TimeUnit.SECONDS);

            assertNotNull(firstResult);
            assertNotNull(secondResult);
            assertEquals(1, countingAdapter.deliveries.get());
            assertTrue(notificationPort.findById(notification.id()).isPresent());
            assertEquals(NotificationStatus.SENT,
                    notificationPort.findById(notification.id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    private Notification processAfterStart(UUID id, CountDownLatch start) throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return engine.process(id);
    }

    private Notification savePendingNotificationWithChannel() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "engine-claim-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "test-claim",
                "engine-claim-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "engine-claim-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "ClaimCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "ClaimStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "engine-claim-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "engine claim reason", "АКТИВНО", Set.of(poa)));
        return notificationPort.save(Notification.of(UUID.randomUUID(), subscription, outage,
                "engine claim message", NotificationStatus.PENDING));
    }
}
