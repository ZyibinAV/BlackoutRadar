package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreetJpaRepository extends JpaRepository<StreetEntity, UUID> {

    Optional<StreetEntity> findByCityIdAndTypeAndCanonicalName(UUID cityId, StreetType type, String canonicalName);
}