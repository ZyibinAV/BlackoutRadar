package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationChannelEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannelEntity, UUID> {

    List<NotificationChannelEntity> findByUserId(UUID userId);
}
