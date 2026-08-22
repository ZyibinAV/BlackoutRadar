package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationPersistenceTest {

    @Autowired
    private NotificationPort notificationPort;

    @Autowired
    private NotificationJpaRepository notificationRepository;

    @Autowired
    private SubscriptionPort subscriptionPort;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    @Autowired
    private PowerOutagePort powerOutagePort;

    @Autowired
    private PowerOutageJpaRepository powerOutageRepository;

    @Autowired
    private SourcePort sourcePort;

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
    private TransformerStationPort stationPort;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void saveAndFindByIdRoundTrip() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "Аварийное отключение по адресу Ленина 15", NotificationStatus.PENDING);

        Notification saved = notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
        assertEquals(subscription.id(), found.get().subscription().id());
        assertEquals(powerOutage.id(), found.get().powerOutage().id());
        assertEquals("Аварийное отключение по адресу Ленина 15", found.get().message());
        assertEquals(NotificationStatus.PENDING, found.get().status());
    }

    @Test
    void saveAndFindBySubscriptionAndPowerOutage() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "message", NotificationStatus.PENDING);

        notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort
                .findBySubscriptionAndPowerOutage(subscription.id(), powerOutage.id());

        assertTrue(found.isPresent());
        assertEquals(notification.id(), found.get().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(notificationPort.findById(UUID.randomUUID()).isEmpty());
        assertTrue(notificationPort
                .findBySubscriptionAndPowerOutage(UUID.randomUUID(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void lifecyclePendingToProcessingToSentWithIsolation() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        UUID id = UUID.randomUUID();
        Notification pending = Notification.of(id, subscription, powerOutage, "msg",
                NotificationStatus.PENDING);

        notificationPort.save(pending);
        entityManager.flush();
        entityManager.clear();

        Notification loaded = notificationPort.findById(id).orElseThrow();
        Notification processing = loaded.startProcessing();

        notificationPort.save(processing);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> afterProcessing = notificationPort.findById(id);
        assertTrue(afterProcessing.isPresent());
        assertEquals(NotificationStatus.PROCESSING, afterProcessing.get().status());
        assertEquals(id, afterProcessing.get().id());

        Notification toSend = afterProcessing.get().markSent();
        notificationPort.save(toSend);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort.findById(id);
        assertTrue(found.isPresent());
        assertEquals(NotificationStatus.SENT, found.get().status());
        assertEquals(id, found.get().id());
        assertEquals(subscription.id(), found.get().subscription().id());
        assertEquals(powerOutage.id(), found.get().powerOutage().id());
        assertEquals("msg", found.get().message());
    }

    @Test
    void lifecycleProcessingToFailedToProcessingWithIsolation() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        UUID id = UUID.randomUUID();
        Notification pending = Notification.of(id, subscription, powerOutage, "msg",
                NotificationStatus.PENDING);
        notificationPort.save(pending);
        entityManager.flush();
        entityManager.clear();

        Notification processing = notificationPort.findById(id).orElseThrow().startProcessing();
        notificationPort.save(processing);
        entityManager.flush();
        entityManager.clear();

        Notification failed = notificationPort.findById(id).orElseThrow().markFailed();
        notificationPort.save(failed);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> afterFailed = notificationPort.findById(id);
        assertTrue(afterFailed.isPresent());
        assertEquals(NotificationStatus.FAILED, afterFailed.get().status());
        assertEquals(id, afterFailed.get().id());

        Notification reprocessing = afterFailed.get().startProcessing();
        notificationPort.save(reprocessing);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort.findById(id);
        assertTrue(found.isPresent());
        assertEquals(NotificationStatus.PROCESSING, found.get().status());
        assertEquals(id, found.get().id());
    }

    @Test
    void identityPreservedAfterLifecycleUpdate() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        UUID id = UUID.randomUUID();
        Notification original = Notification.of(id, subscription, powerOutage, "msg",
                NotificationStatus.PENDING);
        notificationPort.save(original);
        entityManager.flush();
        entityManager.clear();

        for (NotificationStatus expected : new NotificationStatus[]{
                NotificationStatus.PROCESSING, NotificationStatus.SENT}) {
            Notification current = notificationPort.findById(id).orElseThrow();
            Notification next = expected == NotificationStatus.PROCESSING
                    ? current.startProcessing()
                    : current.markSent();
            notificationPort.save(next);
            entityManager.flush();
            entityManager.clear();
            Optional<Notification> found = notificationPort.findById(id);
            assertTrue(found.isPresent());
            assertEquals(id, found.get().id());
            assertEquals(expected, found.get().status());
            if (expected == NotificationStatus.SENT) break;
        }
    }

    @Test
    void uniquenessDuplicateSubscriptionPowerOutageRejected() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification first = Notification.of(UUID.randomUUID(), subscription, powerOutage, "first",
                NotificationStatus.PENDING);
        Notification second = Notification.of(UUID.randomUUID(), subscription, powerOutage, "second",
                NotificationStatus.PENDING);

        notificationPort.save(first);
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> notificationPort.save(second));
    }

    @Test
    void uniquenessAllowsDifferentCombinations() {
        Subscription subA = saveSubscription();
        Subscription subB = saveSubscription();
        PowerOutage outA = savePowerOutage();
        PowerOutage outB = savePowerOutage();

        Notification n1 = Notification.of(UUID.randomUUID(), subA, outA, "m1",
                NotificationStatus.PENDING);
        Notification n2 = Notification.of(UUID.randomUUID(), subA, outB, "m2",
                NotificationStatus.PENDING);
        Notification n3 = Notification.of(UUID.randomUUID(), subB, outA, "m3",
                NotificationStatus.PENDING);

        notificationPort.save(n1);
        notificationPort.save(n2);
        notificationPort.save(n3);
        entityManager.flush();
        entityManager.clear();

        assertTrue(notificationPort.findBySubscriptionAndPowerOutage(subA.id(), outA.id()).isPresent());
        assertTrue(notificationPort.findBySubscriptionAndPowerOutage(subA.id(), outB.id()).isPresent());
        assertTrue(notificationPort.findBySubscriptionAndPowerOutage(subB.id(), outA.id()).isPresent());
        assertEquals(n1.id(), notificationPort.findBySubscriptionAndPowerOutage(subA.id(), outA.id()).get().id());
    }

    @Test
    void foreignKeySubscriptionMustExist() {
        PowerOutage powerOutage = savePowerOutage();
        Subscription fakeSubscription = fakeSubscription();
        Notification notification = Notification.of(UUID.randomUUID(), fakeSubscription, powerOutage,
                "msg", NotificationStatus.PENDING);

        assertThrows(DataIntegrityViolationException.class, () -> notificationPort.save(notification));
    }

    @Test
    void foreignKeyPowerOutageMustExist() {
        Subscription subscription = saveSubscription();
        PowerOutage fakeOutage = fakePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, fakeOutage,
                "msg", NotificationStatus.PENDING);

        assertThrows(DataIntegrityViolationException.class, () -> notificationPort.save(notification));
    }

    @Test
    void restrictDeleteSubscriptionWhenNotificationExists() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "msg", NotificationStatus.PENDING);
        notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            subscriptionRepository.deleteById(subscription.id());
            subscriptionRepository.flush();
        });
    }

    @Test
    void restrictDeletePowerOutageWhenNotificationExists() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "msg", NotificationStatus.PENDING);
        notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            powerOutageRepository.deleteById(powerOutage.id());
            powerOutageRepository.flush();
        });
    }

    @Test
    void mappingRoundTripPreservesAllFieldsWithIsolation() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        UUID id = UUID.randomUUID();
        String message = "Полное отключение с 10:00 до 15:00";
        Notification original = Notification.of(id, subscription, powerOutage, message,
                NotificationStatus.PENDING);

        notificationPort.save(original);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort.findById(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(subscription.id(), found.get().subscription().id());
        assertEquals(powerOutage.id(), found.get().powerOutage().id());
        assertEquals(message, found.get().message());
        assertEquals(NotificationStatus.PENDING, found.get().status());
        // subscription and powerOutage domain graphs are hydrated
        assertNotNull(found.get().subscription().user());
        assertNotNull(found.get().subscription().address());
        assertNotNull(found.get().powerOutage().source());
        assertFalse(found.get().powerOutage().addresses().isEmpty());
    }

    @Test
    void timestampsAreManaged() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "msg", NotificationStatus.PENDING);
        notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        NotificationEntity entity = notificationRepository.findById(notification.id()).orElseThrow();
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        Instant createdAt = entity.getCreatedAt();
        Instant updatedAt = entity.getUpdatedAt();

        // lifecycle update should bump updated_at
        Notification processing = notificationPort.findById(notification.id()).orElseThrow()
                .startProcessing();
        notificationPort.save(processing);
        entityManager.flush();
        entityManager.clear();

        NotificationEntity updated = notificationRepository.findById(notification.id()).orElseThrow();
        assertEquals(createdAt, updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().toEpochMilli() >= updatedAt.toEpochMilli());
    }

    @Test
    void findBySubscriptionAndPowerOutageIsIsolated() {
        Subscription subscription = saveSubscription();
        PowerOutage powerOutage = savePowerOutage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "isolated", NotificationStatus.PENDING);
        notificationPort.save(notification);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationPort.findBySubscriptionAndPowerOutage(
                subscription.id(), powerOutage.id());
        assertTrue(found.isPresent());
        assertEquals(NotificationStatus.PENDING, found.get().status());
        // mutate via lifecycle and verify fresh read isolates
        Notification processing = found.get().startProcessing();
        notificationPort.save(processing);
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> fresh = notificationPort.findBySubscriptionAndPowerOutage(
                subscription.id(), powerOutage.id());
        assertTrue(fresh.isPresent());
        assertEquals(NotificationStatus.PROCESSING, fresh.get().status());
        assertEquals(notification.id(), fresh.get().id());
    }

    // --- helpers ---

    private Subscription saveSubscription() {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "user-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "Область-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "Ленина-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return subscriptionPort.save(Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS)));
    }

    private PowerOutage savePowerOutage() {
        Source source = sourcePort.save(Source.of(UUID.randomUUID(),
                "source-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        Region region = regionPort.save(Region.of(UUID.randomUUID(),
                "Область-" + UUID.randomUUID()));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET,
                "Ленина-" + UUID.randomUUID()));
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street,
                new House("15", null, "15")));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное отключение", "АКТИВНО", Set.of(poa));
        return powerOutagePort.save(outage);
    }

    private Subscription fakeSubscription() {
        User user = User.of(UUID.randomUUID(), "fake-" + UUID.randomUUID() + "@example.com",
                UserRole.USER, true);
        Region region = Region.of(UUID.randomUUID(), "Fake");
        City city = City.of(UUID.randomUUID(), region, "FakeCity");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "FakeStreet");
        Address address = Address.of(UUID.randomUUID(), street, new House("1", null, "1"));
        Instant start = Instant.now();
        return Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS));
    }

    private PowerOutage fakePowerOutage() {
        Source source = Source.of(UUID.randomUUID(), "fake-src-" + UUID.randomUUID(),
                "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Region region = Region.of(UUID.randomUUID(), "FakeRegion");
        City city = City.of(UUID.randomUUID(), region, "FakeCity2");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "FakeStreet2");
        Address address = Address.of(UUID.randomUUID(), street, new House("2", null, "2"));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        return PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "fake", "АКТИВНО", Set.of(poa));
    }
}
