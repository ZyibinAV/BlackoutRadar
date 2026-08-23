package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityDistrictJpaRepository extends JpaRepository<CityDistrictEntity, UUID> {

    Optional<CityDistrictEntity> findByCityIdAndName(UUID cityId, String name);

    @Modifying
    @Query(value = "INSERT INTO city_district (id, city_id, name, created_at, updated_at) VALUES (:id, :cityId, :name, NOW(), NOW()) ON CONFLICT (city_id, name) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("cityId") UUID cityId, @Param("name") String name);
}