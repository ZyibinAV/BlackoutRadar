package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PowerOutageJpaRepository extends JpaRepository<PowerOutageEntity, UUID> {

    Optional<PowerOutageEntity> findBySourceIdAndExternalReference(UUID sourceId, String externalReference);

    Optional<PowerOutageEntity> findBySourceIdAndFallbackFingerprint(UUID sourceId, String fallbackFingerprint);

    @Modifying
    @Query(value = "INSERT INTO power_outage (id, source_id, start_time, end_time, reason, status, external_reference, fallback_fingerprint, created_at, updated_at) VALUES (:id, :sourceId, :startTime, :endTime, :reason, :status, :externalReference, NULL, NOW(), NOW()) ON CONFLICT (source_id, external_reference) WHERE external_reference IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertWithExternalReference(@Param("id") UUID id, @Param("sourceId") UUID sourceId, @Param("startTime") java.time.Instant startTime, @Param("endTime") java.time.Instant endTime, @Param("reason") String reason, @Param("status") String status, @Param("externalReference") String externalReference);

    @Modifying
    @Query(value = "INSERT INTO power_outage (id, source_id, start_time, end_time, reason, status, external_reference, fallback_fingerprint, created_at, updated_at) VALUES (:id, :sourceId, :startTime, :endTime, :reason, :status, NULL, :fallbackFingerprint, NOW(), NOW()) ON CONFLICT (source_id, fallback_fingerprint) WHERE fallback_fingerprint IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertWithFallbackFingerprint(@Param("id") UUID id, @Param("sourceId") UUID sourceId, @Param("startTime") java.time.Instant startTime, @Param("endTime") java.time.Instant endTime, @Param("reason") String reason, @Param("status") String status, @Param("fallbackFingerprint") String fallbackFingerprint);
}