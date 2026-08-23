package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionalDistrictEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionalDistrictJpaRepository extends JpaRepository<RegionalDistrictEntity, UUID> {

    Optional<RegionalDistrictEntity> findByRegionIdAndTypeAndName(UUID regionId, RegionalDistrictType type, String name);

    @Modifying
    @Query(value = "INSERT INTO regional_district (id, region_id, type, name, created_at, updated_at) VALUES (:id, :regionId, CAST(:type AS VARCHAR), :name, NOW(), NOW()) ON CONFLICT (region_id, type, name) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("regionId") UUID regionId, @Param("type") String type, @Param("name") String name);
}