package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationDeliveryStuckDetectionTest {

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

    @Test
    void processingWithOldIncompleteAttemptIsFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant startedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertEquals(List.of(delivery.id()),
                found.stream().map(NotificationDelivery::id).toList());
    }

    @Test
    void startedExactlyAtThresholdIsNotFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant threshold = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, threshold));

        List<NotificationDelivery> found = deliveryPort.findStuckDeliveries(threshold, 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void processingWithRecentIncompleteAttemptIsNotFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant startedAt = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now().minus(1, ChronoUnit.HOURS), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void readyWithIncompleteAttemptIsNotCandidate() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY);
        Instant startedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, startedAt));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void terminalStatusesAreNotCandidates() {
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        NotificationDelivery sent = saveDelivery(DeliveryStatus.SENT);
        NotificationDelivery failed = saveDelivery(DeliveryStatus.FAILED);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), sent, 1, old));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), failed, 1, old));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void processingWithOnlyCompletedAttemptIsNotFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant startedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1, startedAt,
                startedAt.plus(2, ChronoUnit.SECONDS), DeliveryAttemptResult.SUCCESS, null));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void processingWithoutAttemptsIsNotFound() {
        saveDelivery(DeliveryStatus.PROCESSING);

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void historyWithCompletedAndCurrentIncompleteAttemptIsFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant old = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1, old,
                old.plus(2, ChronoUnit.SECONDS), DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT"));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 2, old));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertEquals(List.of(delivery.id()),
                found.stream().map(NotificationDelivery::id).toList());
    }

    @Test
    void searchDoesNotMixDeliveries() {
        NotificationDelivery stuck = saveDelivery(DeliveryStatus.PROCESSING);
        NotificationDelivery healthy = saveDelivery(DeliveryStatus.PROCESSING);
        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), stuck, 1, old));
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), healthy, 1, old,
                old.plus(2, ChronoUnit.SECONDS), DeliveryAttemptResult.SUCCESS, null));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(Instant.now(), 10);

        assertEquals(List.of(stuck.id()),
                found.stream().map(NotificationDelivery::id).toList());
    }

    @Test
    void oldCompletedAndFreshIncompleteIsNotFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant completedStart = base.minus(2, ChronoUnit.HOURS);
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1, completedStart,
                completedStart.plus(2, ChronoUnit.SECONDS), DeliveryAttemptResult.SUCCESS, null));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 2,
                base.minus(5, ChronoUnit.MINUTES)));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(base.minus(1, ChronoUnit.HOURS), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void oldIncompleteAndNewerIncompleteAlreadyOldIsFound() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1,
                base.minus(2, ChronoUnit.HOURS)));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 2,
                base.minus(90, ChronoUnit.MINUTES)));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(base.minus(1, ChronoUnit.HOURS), 10);

        assertEquals(List.of(delivery.id()),
                found.stream().map(NotificationDelivery::id).toList());
    }

    @Test
    void recoveredOldIncompleteDoesNotCauseRepeatedStuck() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.READY);
        com.zyibin.app.blackoutradar.application.notification.DeliveryClaim first =
                fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant threshold = base.minus(1, ChronoUnit.HOURS);
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), first.delivery(), 1,
                base.minus(2, ChronoUnit.HOURS)));

        assertTrue(fencingPort.recoverStuck(delivery.id(), threshold));

        com.zyibin.app.blackoutradar.application.notification.DeliveryClaim second =
                fencingPort.claim(delivery.id(), Instant.now()).orElseThrow();
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), second.delivery(), 2,
                base.minus(5, ChronoUnit.MINUTES)));

        List<NotificationDelivery> found = deliveryPort.findStuckDeliveries(threshold, 10);

        assertTrue(found.isEmpty());
        List<DeliveryAttempt> history = attemptPort.findByNotificationDeliveryId(delivery.id());
        assertEquals(2, history.size());
        assertFalse(history.get(0).isCompleted());
        assertFalse(history.get(1).isCompleted());
    }

    @Test
    void multipleCompletedAttemptsDoNotAffectFreshIncomplete() {
        NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant old = base.minus(2, ChronoUnit.HOURS);
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1, old,
                old.plus(2, ChronoUnit.SECONDS), DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT"));
        attemptPort.save(DeliveryAttempt.completed(UUID.randomUUID(), delivery, 2, old,
                old.plus(3, ChronoUnit.SECONDS), DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT"));
        attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 3,
                base.minus(5, ChronoUnit.MINUTES)));

        List<NotificationDelivery> found =
                deliveryPort.findStuckDeliveries(base.minus(1, ChronoUnit.HOURS), 10);

        assertTrue(found.isEmpty());
    }

    @Test
    void searchRespectsLimit() {        Instant old = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 3; i++) {
            NotificationDelivery delivery = saveDelivery(DeliveryStatus.PROCESSING);
            attemptPort.save(DeliveryAttempt.started(UUID.randomUUID(), delivery, 1, old));
        }

        assertEquals(2, deliveryPort.findStuckDeliveries(Instant.now(), 2).size());
    }

    @Test
    void searchRejectsInvalidLimit() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> deliveryPort.findStuckDeliveries(now, 0));
        assertThrows(IllegalArgumentException.class, () -> deliveryPort.findStuckDeliveries(now, -1));
    }

    @Test
    void searchRejectsNullThreshold() {
        assertThrows(NullPointerException.class, () -> deliveryPort.findStuckDeliveries(null, 10));
    }

    private NotificationDelivery saveDelivery(DeliveryStatus status) {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "stuck-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        NotificationChannel channel = channelPort.save(NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal-" + UUID.randomUUID() + "@example.com", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "stuck-region-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "StuckCity"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "StuckStreet-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Subscription subscription = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user,
                address, start, start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "stuck-source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = powerOutagePort.save(PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "stuck reason", "АКТИВНО", Set.of(poa)));
        Notification notification = notificationPort.save(Notification.of(UUID.randomUUID(),
                subscription, outage, "stuck message", NotificationStatus.PENDING));
        return deliveryPort.save(NotificationDelivery.of(UUID.randomUUID(), notification, channel,
                status, null));
    }
}
