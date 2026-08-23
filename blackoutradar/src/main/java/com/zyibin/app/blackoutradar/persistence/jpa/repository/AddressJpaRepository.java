package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressJpaRepository extends JpaRepository<AddressEntity, UUID> {

    Optional<AddressEntity> findByStreetIdAndCityDistrictIsNullAndCanonicalHouse(UUID streetId, String canonicalHouse);

    Optional<AddressEntity> findByStreetIdAndCityDistrictIdAndCanonicalHouse(UUID streetId, UUID cityDistrictId, String canonicalHouse);

    @Modifying
    @Query(value = "INSERT INTO address (id, city_id, street_id, city_district_id, house_number, house_addition, canonical_house, created_at, updated_at) VALUES (:id, :cityId, :streetId, NULL, :houseNumber, :houseAddition, :canonicalHouse, NOW(), NOW()) ON CONFLICT (street_id, canonical_house) WHERE city_district_id IS NULL DO NOTHING", nativeQuery = true)
    void insertIfAbsentWithoutDistrict(@Param("id") UUID id, @Param("cityId") UUID cityId, @Param("streetId") UUID streetId, @Param("houseNumber") String houseNumber, @Param("houseAddition") String houseAddition, @Param("canonicalHouse") String canonicalHouse);

    @Modifying
    @Query(value = "INSERT INTO address (id, city_id, street_id, city_district_id, house_number, house_addition, canonical_house, created_at, updated_at) VALUES (:id, :cityId, :streetId, :cityDistrictId, :houseNumber, :houseAddition, :canonicalHouse, NOW(), NOW()) ON CONFLICT (street_id, city_district_id, canonical_house) WHERE city_district_id IS NOT NULL DO NOTHING", nativeQuery = true)
    void insertIfAbsentWithDistrict(@Param("id") UUID id, @Param("cityId") UUID cityId, @Param("streetId") UUID streetId, @Param("cityDistrictId") UUID cityDistrictId, @Param("houseNumber") String houseNumber, @Param("houseAddition") String houseAddition, @Param("canonicalHouse") String canonicalHouse);
}