package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressJpaRepository extends JpaRepository<AddressEntity, UUID> {

    Optional<AddressEntity> findByStreetIdAndCityDistrictIsNullAndCanonicalHouse(UUID streetId, String canonicalHouse);

    Optional<AddressEntity> findByStreetIdAndCityDistrictIdAndCanonicalHouse(UUID streetId, UUID cityDistrictId, String canonicalHouse);
}