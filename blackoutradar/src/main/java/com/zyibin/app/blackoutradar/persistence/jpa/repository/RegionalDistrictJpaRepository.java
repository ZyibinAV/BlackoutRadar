package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionalDistrictEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionalDistrictJpaRepository extends JpaRepository<RegionalDistrictEntity, UUID> {

    Optional<RegionalDistrictEntity> findByRegionIdAndTypeAndName(UUID regionId, RegionalDistrictType type, String name);
}