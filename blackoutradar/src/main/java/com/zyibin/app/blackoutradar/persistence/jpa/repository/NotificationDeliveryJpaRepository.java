package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationDeliveryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    List<NotificationDeliveryEntity> findByNotificationId(UUID notificationId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationDeliveryEntity d SET d.status = com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus.PROCESSING, d.processingToken = :token, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id AND d.status = com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus.READY AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now)")
    int claimReadyAsProcessingWithToken(@Param("id") UUID id, @Param("now") Instant now,
                                        @Param("token") UUID token);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationDeliveryEntity d SET d.status = :status, d.nextAttemptAt = :nextAttemptAt, d.processingToken = NULL, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id AND d.status = com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus.PROCESSING AND d.processingToken = :token")
    int updateIfOwned(@Param("id") UUID id, @Param("token") UUID token,
                      @Param("status") DeliveryStatus status,
                      @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notification_delivery d SET status = 'READY', processing_token = NULL, updated_at = CURRENT_TIMESTAMP WHERE d.id = :id AND d.status = 'PROCESSING' AND EXISTS (SELECT 1 FROM delivery_attempt a WHERE a.notification_delivery_id = d.id AND a.completed_at IS NULL AND a.started_at < :threshold) AND NOT EXISTS (SELECT 1 FROM delivery_attempt a2 WHERE a2.notification_delivery_id = d.id AND a2.completed_at IS NULL AND a2.started_at >= :threshold)", nativeQuery = true)
    int recoverStuck(@Param("id") UUID id, @Param("threshold") Instant threshold);

    @Query("SELECT d FROM NotificationDeliveryEntity d WHERE d.status = com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus.READY AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now) ORDER BY d.nextAttemptAt ASC NULLS FIRST, d.createdAt ASC")
    List<NotificationDeliveryEntity> findDueForProcessing(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT d FROM NotificationDeliveryEntity d WHERE d.status = com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus.PROCESSING AND EXISTS (SELECT 1 FROM DeliveryAttemptEntity a WHERE a.notificationDelivery.id = d.id AND a.completedAt IS NULL AND a.startedAt < :threshold) AND NOT EXISTS (SELECT 1 FROM DeliveryAttemptEntity a2 WHERE a2.notificationDelivery.id = d.id AND a2.completedAt IS NULL AND a2.startedAt >= :threshold) ORDER BY d.createdAt ASC")
    List<NotificationDeliveryEntity> findStuckDeliveries(@Param("threshold") Instant threshold,
                                                         Pageable pageable);
}
