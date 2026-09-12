package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.outage.OutageTestData;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.DeliveryAttemptEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationDeliveryEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class DeliveryAttemptMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private DeliveryAttemptMapper mapper;

    private NotificationDelivery delivery() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Subscription subscription = Subscription.of(UUID.randomUUID(), user, OutageTestData.address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
        PowerOutage powerOutage = OutageTestData.outage();
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
        NotificationChannel channel = NotificationChannel.of(UUID.randomUUID(), user, "telegram",
                "123456789", true);
        return NotificationDelivery.of(UUID.randomUUID(), notification, channel);
    }

    @Test
    void mapsDomainToEntity() {
        NotificationDelivery delivery = delivery();
        UUID id = UUID.randomUUID();
        Instant completedAt = NOW.plus(2, ChronoUnit.SECONDS);
        DeliveryAttempt attempt = DeliveryAttempt.completed(id, delivery, 2,
                NOW, completedAt, DeliveryAttemptResult.TEMPORARY_FAILURE, "SMTP_TIMEOUT");

        DeliveryAttemptEntity entity = mapper.toEntity(attempt);

        assertEquals(id, entity.getId());
        assertEquals(2, entity.getAttemptNumber());
        assertEquals(NOW, entity.getStartedAt());
        assertEquals(completedAt, entity.getCompletedAt());
        assertEquals(DeliveryAttemptResult.TEMPORARY_FAILURE, entity.getResult());
        assertEquals("SMTP_TIMEOUT", entity.getErrorCode());
    }

    @Test
    void mapsStartedEntityToDomain() {
        NotificationDelivery delivery = delivery();
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        entity.setId(UUID.randomUUID());
        entity.setNotificationDelivery(deliveryEntity(delivery.id()));
        entity.setAttemptNumber(1);
        entity.setStartedAt(NOW);

        DeliveryAttempt attempt = mapper.toDomain(entity, delivery);

        assertEquals(entity.getId(), attempt.id());
        assertEquals(delivery, attempt.notificationDelivery());
        assertEquals(1, attempt.attemptNumber());
        assertEquals(NOW, attempt.startedAt());
        assertNull(attempt.completedAt());
        assertNull(attempt.result());
        assertNull(attempt.errorCode());
    }

    @Test
    void mapsCompletedEntityToDomain() {
        NotificationDelivery delivery = delivery();
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        entity.setId(UUID.randomUUID());
        entity.setNotificationDelivery(deliveryEntity(delivery.id()));
        entity.setAttemptNumber(3);
        entity.setStartedAt(NOW);
        entity.setCompletedAt(NOW.plus(5, ChronoUnit.SECONDS));
        entity.setResult(DeliveryAttemptResult.SUCCESS);

        DeliveryAttempt attempt = mapper.toDomain(entity, delivery);

        assertEquals(DeliveryAttemptResult.SUCCESS, attempt.result());
        assertEquals(NOW.plus(5, ChronoUnit.SECONDS), attempt.completedAt());
    }

    @Test
    void roundTripPreservesData() {
        NotificationDelivery delivery = delivery();
        DeliveryAttempt original = DeliveryAttempt.completed(UUID.randomUUID(), delivery, 1,
                NOW, NOW.plus(1, ChronoUnit.SECONDS), DeliveryAttemptResult.PERMANENT_FAILURE, "BAD_ADDRESS");

        DeliveryAttempt restored = mapper.toDomain(mapper.toEntity(original), delivery);

        assertEquals(original.id(), restored.id());
        assertEquals(1, restored.attemptNumber());
        assertEquals(original.startedAt(), restored.startedAt());
        assertEquals(original.completedAt(), restored.completedAt());
        assertEquals(DeliveryAttemptResult.PERMANENT_FAILURE, restored.result());
        assertEquals("BAD_ADDRESS", restored.errorCode());
    }

    private NotificationDeliveryEntity deliveryEntity(UUID id) {
        NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
        entity.setId(id);
        return entity;
    }
}
