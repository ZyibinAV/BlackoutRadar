package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class NotificationMapperTest {

    @Autowired
    private NotificationMapper mapper;

    @Test
    void mapsDomainToEntityPreservesSimpleFields() {
        Notification notification = newNotification(NotificationStatus.PENDING);

        NotificationEntity entity = mapper.toEntity(notification);

        assertEquals(notification.id(), entity.getId());
        assertEquals(notification.message(), entity.getMessage());
        assertEquals(notification.status(), entity.getStatus());
        // subscription/powerOutage are ignored in mapper and set manually in adapter
        assertNull(entity.getSubscription());
        assertNull(entity.getPowerOutage());
    }

    @Test
    void mapsEntityToDomainWithAssociations() {
        NotificationEntity entity = new NotificationEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setMessage("Аварийное отключение по адресу");
        entity.setStatus(NotificationStatus.PROCESSING);

        Subscription subscription = newSubscription();
        PowerOutage powerOutage = newPowerOutage();

        Notification restored = mapper.toDomain(entity, subscription, powerOutage);

        assertEquals(id, restored.id());
        assertEquals("Аварийное отключение по адресу", restored.message());
        assertEquals(NotificationStatus.PROCESSING, restored.status());
        assertEquals(subscription.id(), restored.subscription().id());
        assertEquals(powerOutage.id(), restored.powerOutage().id());
    }

    @Test
    void roundTripPreservesIdentityAndStatus() {
        Notification original = newNotification(NotificationStatus.SENT);

        NotificationEntity entity = mapper.toEntity(original);
        // simulate adapter setting associations
        Subscription subscription = original.subscription();
        PowerOutage powerOutage = original.powerOutage();

        Notification restored = mapper.toDomain(entity, subscription, powerOutage);

        assertEquals(original.id(), restored.id());
        assertEquals(original.message(), restored.message());
        assertEquals(original.status(), restored.status());
        assertEquals(original.subscription().id(), restored.subscription().id());
        assertEquals(original.powerOutage().id(), restored.powerOutage().id());
    }

    @Test
    void mapsAllStatuses() {
        for (NotificationStatus status : NotificationStatus.values()) {
            Notification notification = newNotification(status);
            NotificationEntity entity = mapper.toEntity(notification);
            assertEquals(status, entity.getStatus());

            Notification restored = mapper.toDomain(entity, notification.subscription(),
                    notification.powerOutage());
            assertEquals(status, restored.status());
        }
    }

    @Test
    void toEntityDoesNotExposeTimestamps() {
        Notification notification = newNotification(NotificationStatus.PENDING);
        NotificationEntity entity = mapper.toEntity(notification);
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    private Notification newNotification(NotificationStatus status) {
        Subscription subscription = newSubscription();
        PowerOutage powerOutage = newPowerOutage();
        return Notification.of(UUID.randomUUID(), subscription, powerOutage,
                "Аварийное отключение по адресу", status);
    }

    private Subscription newSubscription() {
        User user = User.of(UUID.randomUUID(), "user-" + UUID.randomUUID() + "@example.com",
                UserRole.USER, true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS));
    }

    private PowerOutage newPowerOutage() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть-" + UUID.randomUUID(),
                "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Address addressA = newAddress();
        Address addressB = newAddress();
        TransformerStation station = TransformerStation.of(UUID.randomUUID(), "ТП-" + UUID.randomUUID());
        PowerOutageAddress poaA = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressA);
        PowerOutageAddress poaB = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressB, station);
        return PowerOutage.of(UUID.randomUUID(), source, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T05:00:00Z"), "Аварийное отключение", "АКТИВНО",
                List.of(poaA, poaB));
    }

    private Address newAddress() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина-" + UUID.randomUUID());
        return Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }
}
