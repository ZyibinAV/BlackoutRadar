package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.outage.OutageTestData;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationChannelEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationDeliveryEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class NotificationDeliveryMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private NotificationDeliveryMapper mapper;

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private Notification notification() {
        Subscription subscription = Subscription.of(UUID.randomUUID(), user(), OutageTestData.address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
        PowerOutage powerOutage = OutageTestData.outage();
        return Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
    }

    private NotificationChannel channel() {
        return NotificationChannel.of(UUID.randomUUID(), user(), "email", "personal@example.com", true);
    }

    @Test
    void mapsDomainToEntity() {
        Notification notification = notification();
        NotificationChannel channel = channel();
        UUID id = UUID.randomUUID();
        Instant nextAttemptAt = NOW.plus(5, ChronoUnit.MINUTES);
        NotificationDelivery delivery = NotificationDelivery.of(id, notification, channel,
                DeliveryStatus.PROCESSING, nextAttemptAt);

        NotificationDeliveryEntity entity = mapper.toEntity(delivery);

        assertEquals(id, entity.getId());
        assertEquals(DeliveryStatus.PROCESSING, entity.getStatus());
        assertEquals(nextAttemptAt, entity.getNextAttemptAt());
    }

    @Test
    void mapsEntityToDomain() {
        Notification notification = notification();
        NotificationChannel channel = channel();
        NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
        entity.setId(UUID.randomUUID());
        entity.setNotification(notificationEntity(notification.id()));
        entity.setNotificationChannel(channelEntity(channel.id()));
        entity.setStatus(DeliveryStatus.SENT);
        entity.setNextAttemptAt(null);

        NotificationDelivery delivery = mapper.toDomain(entity, notification, channel);

        assertEquals(entity.getId(), delivery.id());
        assertEquals(notification, delivery.notification());
        assertEquals(channel, delivery.notificationChannel());
        assertEquals(DeliveryStatus.SENT, delivery.status());
        assertNull(delivery.nextAttemptAt());
    }

    @Test
    void roundTripPreservesData() {
        Notification notification = notification();
        NotificationChannel channel = channel();
        Instant nextAttemptAt = NOW.plus(5, ChronoUnit.MINUTES);
        NotificationDelivery original = NotificationDelivery.of(UUID.randomUUID(), notification,
                channel, DeliveryStatus.READY, nextAttemptAt);

        NotificationDelivery restored = mapper.toDomain(mapper.toEntity(original), notification, channel);

        assertEquals(original.id(), restored.id());
        assertEquals(original.status(), restored.status());
        assertEquals(original.nextAttemptAt(), restored.nextAttemptAt());
        assertEquals(notification.id(), restored.notification().id());
        assertEquals(channel.id(), restored.notificationChannel().id());
    }

    private NotificationEntity notificationEntity(UUID id) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        return entity;
    }

    private NotificationChannelEntity channelEntity(UUID id) {
        NotificationChannelEntity entity = new NotificationChannelEntity();
        entity.setId(id);
        entity.setUser(userEntity());
        entity.setType("email");
        entity.setDestination("personal@example.com");
        entity.setEnabled(true);
        return entity;
    }

    private UserEntity userEntity() {
        User user = user();
        UserEntity entity = new UserEntity();
        entity.setId(user.id());
        entity.setEmail(user.email());
        entity.setRole(user.role());
        entity.setActive(user.isActive());
        return entity;
    }
}
