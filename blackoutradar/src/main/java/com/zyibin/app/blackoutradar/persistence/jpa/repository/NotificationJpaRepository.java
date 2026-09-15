package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Optional<NotificationEntity> findBySubscriptionIdAndPowerOutageId(UUID subscriptionId,
                                                                       UUID powerOutageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NotificationEntity n WHERE n.id = :id")
    Optional<NotificationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.status = com.zyibin.app.blackoutradar.domain.notification.NotificationStatus.PROCESSING, n.updatedAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.status = com.zyibin.app.blackoutradar.domain.notification.NotificationStatus.PENDING")
    int claimPendingAsProcessing(@Param("id") UUID id);

    @Modifying
    @Query(value = "INSERT INTO notification (id, subscription_id, power_outage_id, message, status, created_at, updated_at) VALUES (:id, :subscriptionId, :powerOutageId, :message, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (subscription_id, power_outage_id) DO NOTHING", nativeQuery = true)
    int insertIgnore(@Param("id") UUID id,
                     @Param("subscriptionId") UUID subscriptionId,
                     @Param("powerOutageId") UUID powerOutageId,
                     @Param("message") String message,
                     @Param("status") String status);
}
