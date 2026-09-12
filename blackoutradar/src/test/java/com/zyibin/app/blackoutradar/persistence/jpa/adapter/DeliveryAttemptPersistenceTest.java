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
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DeliveryAttemptPersistenceTest {

    @Autowired
    private DeliveryAttemptPort attemptPort;

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
    void saveStartedAttemptRoundTrip() {
        NotificationDelivery delivery = saveDelivery();
        UUID id = UUID.randomUUID();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        DeliveryAttempt attempt = DeliveryAttempt.started(id, delivery, 1, startedAt);

        DeliveryAttempt saved = attemptPort.save(attempt);

        assertEquals(id, saved.id());

        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(delivery.id());

        assertEquals(1, history.size());
        assertEquals(id, history.get(0).id());
        assertEquals(delivery.id(), history.get(0).notificationDelivery().id());
        assertEquals(1, history.get(0).attemptNumber());
        assertEquals(startedAt, history.get(0).startedAt());
        assertNull(history.get(0).completedAt());
        assertNull(history.get(0).result());
        assertNull(history.get(0).errorCode());
    }

    @Test
    void saveCompletedAttemptRoundTrip() {
        NotificationDelivery delivery = saveDelivery();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant completedAt = startedAt.plus(2, ChronoUnit.SECONDS);
        DeliveryAttempt attempt = DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1,
                startedAt, completedAt, DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT");

        attemptPort.save(attempt);

        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(delivery.id());

        assertEquals(1, history.size());
        assertEquals(completedAt, history.get(0).completedAt());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, history.get(0).result());
        assertEquals("SMTP_TIMEOUT", history.get(0).errorCode());
    }

    @Test
    void historyOrderedByAttemptNumber() {
        NotificationDelivery delivery = saveDelivery();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 2,
                startedAt, startedAt.plusSeconds(1), DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT"));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt));
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 3,
                startedAt, startedAt.plusSeconds(2), DeliveryAttemptResult.SUCCESS, null));

        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(delivery.id());

        assertEquals(List.of(1, 2, 3),
                history.stream().map(DeliveryAttempt::attemptNumber).toList());
    }

    @Test
    void attemptNumbersIndependentPerDelivery() {
        NotificationDelivery first = saveDelivery();
        NotificationDelivery second = saveDelivery();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), first, 1, startedAt));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), second, 1, startedAt));

        assertEquals(1, attemptPort.findByNotificationDeliveryId(first.id()).size());
        assertEquals(1, attemptPort.findByNotificationDeliveryId(second.id()).size());
    }

    @Test
    void duplicateAttemptNumberRejected() {
        NotificationDelivery delivery = saveDelivery();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt));

        assertThrows(DataIntegrityViolationException.class, () -> attemptPort.save(
                DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt.plusSeconds(1))));
    }

    @Test
    void historyOfUnknownDeliveryReturnsEmpty() {
        assertTrue(attemptPort.findByNotificationDeliveryId(UUID.randomUUID()).isEmpty());
    }

    private NotificationDelivery saveDelivery() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "attempt-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "attempt-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "AttemptCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "AttemptStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "attempt-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "attempt reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "attempt message", NotificationStatus.PENDING));
        return deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), notification, channel));
    }
}
