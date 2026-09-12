package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.DeliveryAttemptEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {

    List<DeliveryAttemptEntity> findByNotificationDeliveryIdOrderByAttemptNumberAsc(UUID notificationDeliveryId);

    @Query("SELECT COALESCE(MAX(a.attemptNumber), 0) FROM DeliveryAttemptEntity a WHERE a.notificationDelivery.id = :deliveryId")
    int maxAttemptNumber(@Param("deliveryId") UUID deliveryId);
}
