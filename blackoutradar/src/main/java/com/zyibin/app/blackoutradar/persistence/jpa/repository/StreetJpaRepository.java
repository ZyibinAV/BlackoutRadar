package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreetJpaRepository extends JpaRepository<StreetEntity, UUID> {

    Optional<StreetEntity> findByCityIdAndTypeAndCanonicalName(UUID cityId, StreetType type, String canonicalName);

    @Modifying
    @Query(value = "INSERT INTO street (id, city_id, type, canonical_name, created_at, updated_at) VALUES (:id, :cityId, CAST(:type AS VARCHAR), :canonicalName, NOW(), NOW()) ON CONFLICT (city_id, type, canonical_name) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("cityId") UUID cityId, @Param("type") String type, @Param("canonicalName") String canonicalName);
}