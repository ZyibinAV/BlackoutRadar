package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityJpaRepository extends JpaRepository<CityEntity, UUID> {

    Optional<CityEntity> findByRegionIdAndRegionalDistrictIdAndName(UUID regionId, UUID regionalDistrictId, String name);

    Optional<CityEntity> findByRegionIdAndRegionalDistrictIsNullAndName(UUID regionId, String name);
}