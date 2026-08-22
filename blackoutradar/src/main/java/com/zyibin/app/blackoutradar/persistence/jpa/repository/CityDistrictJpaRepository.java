package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityDistrictJpaRepository extends JpaRepository<CityDistrictEntity, UUID> {

    Optional<CityDistrictEntity> findByCityIdAndName(UUID cityId, String name);
}