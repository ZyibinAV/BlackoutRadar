package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityJpaRepository extends JpaRepository<CityEntity, UUID> {

    Optional<CityEntity> findByRegionIdAndRegionalDistrictIdAndName(UUID regionId, UUID regionalDistrictId, String name);

    Optional<CityEntity> findByRegionIdAndRegionalDistrictIsNullAndName(UUID regionId, String name);

    @Modifying
    @Query(value = "INSERT INTO city (id, region_id, regional_district_id, name, created_at, updated_at) VALUES (:id, :regionId, NULL, :name, NOW(), NOW()) ON CONFLICT (region_id, name) WHERE regional_district_id IS NULL DO NOTHING", nativeQuery = true)
    void insertIfAbsentInRegion(@Param("id") UUID id, @Param("regionId") UUID regionId, @Param("name") String name);

    @Modifying
    @Query(value = "INSERT INTO city (id, region_id, regional_district_id, name, created_at, updated_at) VALUES (:id, :regionId, :regionalDistrictId, :name, NOW(), NOW()) ON CONFLICT (regional_district_id, name) WHERE regional_district_id IS NOT NULL DO NOTHING", nativeQuery = true)
    void insertIfAbsentInRegionalDistrict(@Param("id") UUID id, @Param("regionId") UUID regionId, @Param("regionalDistrictId") UUID regionalDistrictId, @Param("name") String name);
}