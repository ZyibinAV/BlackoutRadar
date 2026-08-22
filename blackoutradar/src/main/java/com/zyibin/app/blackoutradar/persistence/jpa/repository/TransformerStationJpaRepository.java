package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransformerStationJpaRepository extends JpaRepository<TransformerStationEntity, UUID> {

    Optional<TransformerStationEntity> findByName(String name);
}