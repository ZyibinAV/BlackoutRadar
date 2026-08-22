package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PowerOutageJpaRepository extends JpaRepository<PowerOutageEntity, UUID> {
}