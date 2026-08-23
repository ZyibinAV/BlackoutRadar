package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionJpaRepository extends JpaRepository<RegionEntity, UUID> {

    Optional<RegionEntity> findByName(String name);

    @Modifying
    @Query(value = "INSERT INTO region (id, name, created_at, updated_at) VALUES (:id, :name, NOW(), NOW()) ON CONFLICT (name) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("name") String name);
}