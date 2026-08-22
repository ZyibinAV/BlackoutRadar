package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionTransformerStationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionTransformerStationJpaRepository
        extends JpaRepository<SubscriptionTransformerStationEntity, UUID> {

    List<SubscriptionTransformerStationEntity> findAllBySubscriptionId(UUID subscriptionId);

    @Modifying
    @Query("delete from SubscriptionTransformerStationEntity s where s.subscription.id = :subscriptionId")
    void deleteBySubscriptionId(@Param("subscriptionId") UUID subscriptionId);
}