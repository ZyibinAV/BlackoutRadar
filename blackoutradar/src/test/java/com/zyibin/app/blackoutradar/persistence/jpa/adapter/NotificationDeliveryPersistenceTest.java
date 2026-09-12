package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationDeliveryPersistenceTest {

    @Autowired
    private NotificationDeliveryPort deliveryPort;

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

    @Test
    void saveAndFindByIdRoundTrip() {
        Fixture fixture = saveFixture();
        UUID id = UUID.randomUUID();
        NotificationDelivery delivery = NotificationDelivery.of(id, fixture.notification(), fixture.channel());

        NotificationDelivery saved = deliveryPort.save(delivery);

        assertEquals(id, saved.id());

        Optional<NotificationDelivery> found = deliveryPort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(fixture.notification().id(), found.get().notification().id());
        assertEquals(fixture.channel().id(), found.get().notificationChannel().id());
        assertEquals(DeliveryStatus.READY, found.get().status());
        assertNull(found.get().nextAttemptAt());
    }

    @Test
    void saveExistingDeliveryIsRejected() {
        Fixture fixture = saveFixture();
        NotificationDelivery delivery = NotificationDelivery.of(UUID.randomUUID(),
                fixture.notification(), fixture.channel());
        deliveryPort.save(delivery);

        assertThrows(IllegalStateException.class, () -> deliveryPort.save(delivery));
        assertEquals(DeliveryStatus.READY,
                deliveryPort.findById(delivery.id()).orElseThrow().status());
    }

    @Test
    void allStatusesAndNextAttemptAtPersisted() {
        Fixture fixture = saveFixture();
        Instant nextAttemptAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        for (DeliveryStatus status : DeliveryStatus.values()) {
            NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                    fixture.user(), "email", status.name().toLowerCase() + "-" + UUID.randomUUID()
                            + "@example.com", true));
            NotificationDelivery saved = deliveryPort.save(NotificationDelivery.of(
                    UUID.randomUUID(), fixture.notification(), channel, status, nextAttemptAt));

            Optional<NotificationDelivery> found = deliveryPort.findById(saved.id());

            assertTrue(found.isPresent());
            assertEquals(status, found.get().status());
            assertEquals(nextAttemptAt.truncatedTo(ChronoUnit.MILLIS),
                    found.get().nextAttemptAt().truncatedTo(ChronoUnit.MILLIS));
        }
    }

    @Test
    void findByNotificationIdReturnsAllChannels() {
        Fixture fixture = saveFixture();
        NotificationChannel second = channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                fixture.user(), "email", "work-" + UUID.randomUUID() + "@example.com", true));
        NotificationChannel telegram = channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                fixture.user(), "telegram", "tg-" + UUID.randomUUID(), true));
        deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), fixture.notification(), fixture.channel()));
        deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), fixture.notification(), second));
        deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), fixture.notification(), telegram));

        List<NotificationDelivery> found = deliveryPort.findByNotificationId(fixture.notification().id());

        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(
                delivery -> delivery.notification().id().equals(fixture.notification().id())));
        assertTrue(found.stream().anyMatch(
                delivery -> delivery.notificationChannel().id().equals(second.id())));
        assertTrue(found.stream().anyMatch(
                delivery -> delivery.notificationChannel().id().equals(telegram.id())));
    }

    @Test
    void sameTypeChannelsAreNotMixed() {
        Fixture fixture = saveFixture();
        NotificationChannel second = channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                fixture.user(), "email", "work-" + UUID.randomUUID() + "@example.com", true));
        NotificationDelivery first = deliveryPort.save(
                NotificationDelivery.of(UUID.randomUUID(), fixture.notification(), fixture.channel()));
        NotificationDelivery next = deliveryPort.save(
                NotificationDelivery.of(UUID.randomUUID(), fixture.notification(), second));

        Optional<NotificationDelivery> foundFirst = deliveryPort.findById(first.id());
        Optional<NotificationDelivery> foundNext = deliveryPort.findById(next.id());

        assertTrue(foundFirst.isPresent());
        assertTrue(foundNext.isPresent());
        assertEquals(fixture.channel().id(), foundFirst.get().notificationChannel().id());
        assertEquals(second.id(), foundNext.get().notificationChannel().id());
    }

    @Test
    void findAbsentIdReturnsEmpty() {
        assertTrue(deliveryPort.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findByNotificationIdWithNoDeliveriesReturnsEmpty() {
        Fixture fixture = saveFixture();

        assertTrue(deliveryPort.findByNotificationId(fixture.notification().id()).isEmpty());
    }

    @Test
    void findReadyForProcessingReturnsOnlyDueDeliveries() {
        Fixture fixture = saveFixture();
        Instant now = Instant.now();
        NotificationDelivery dueNull = saveDelivery(fixture, DeliveryStatus.READY, null);
        NotificationDelivery duePast = saveDelivery(fixture, DeliveryStatus.READY,
                now.minus(1, ChronoUnit.HOURS));
        NotificationDelivery dueNow = saveDelivery(fixture, DeliveryStatus.READY, now);
        saveDelivery(fixture, DeliveryStatus.READY, now.plus(1, ChronoUnit.HOURS));
        saveDelivery(fixture, DeliveryStatus.PROCESSING, null);
        saveDelivery(fixture, DeliveryStatus.SENT, null);
        saveDelivery(fixture, DeliveryStatus.FAILED, null);

        List<NotificationDelivery> found = deliveryPort.findReadyForProcessing(now, 10);

        assertEquals(Set.of(dueNull.id(), duePast.id(), dueNow.id()),
                found.stream().map(NotificationDelivery::id).collect(Collectors.toSet()));
    }

    @Test
    void findReadyForProcessingRespectsLimit() {
        Fixture fixture = saveFixture();
        saveDelivery(fixture, DeliveryStatus.READY, null);
        saveDelivery(fixture, DeliveryStatus.READY, null);
        saveDelivery(fixture, DeliveryStatus.READY, null);

        assertEquals(2, deliveryPort.findReadyForProcessing(Instant.now(), 2).size());
    }

    @Test
    void findReadyForProcessingRejectsInvalidLimit() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> deliveryPort.findReadyForProcessing(now, 0));
        assertThrows(IllegalArgumentException.class, () -> deliveryPort.findReadyForProcessing(now, -1));
    }

    @Test
    void findReadyForProcessingRejectsNullNow() {
        assertThrows(NullPointerException.class, () -> deliveryPort.findReadyForProcessing(null, 10));
    }

    private NotificationDelivery saveDelivery(Fixture fixture, DeliveryStatus status, Instant nextAttemptAt) {
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(),
                fixture.user(), "email", "ready-" + UUID.randomUUID() + "@example.com", true));
        return deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), fixture.notification(),
                channel, status, nextAttemptAt));
    }

    private record Fixture(User user, Notification notification, NotificationChannel channel) {
    }

    private Fixture saveFixture() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "delivery-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "delivery-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "DeliveryCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "DeliveryStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "delivery-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "delivery reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "delivery message", NotificationStatus.PENDING));
        return new Fixture(user, notification, channel);
    }
}
