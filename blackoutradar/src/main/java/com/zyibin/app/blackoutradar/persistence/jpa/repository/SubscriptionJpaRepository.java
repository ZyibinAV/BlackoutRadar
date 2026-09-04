package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.address.id IN :addressIds AND s.isActive = true")
    List<SubscriptionEntity> findActiveByAddressIds(@Param("addressIds") Collection<UUID> addressIds);
}