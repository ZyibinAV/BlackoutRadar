package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Refresh token storage with database-level concurrency protection.
 *
 * <p>Rotation never runs as an unprotected read-check-update-insert sequence:
 * state transitions are single conditional SQL updates, so the database
 * serializes concurrent refresh attempts. No JVM locks are used.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

    List<RefreshTokenEntity> findByUserId(UUID userId);

    /**
     * Atomically revokes the token only while it is still active
     * (not revoked and not expired). Returns 1 for exactly one winner when
     * concurrent requests race on the same token, 0 for all losers.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.tokenHash = :hash"
            + " AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int revokeIfActive(@Param("hash") String tokenHash, @Param("now") Instant now);

    /**
     * Atomically revokes every still-active token of one rotation family.
     * Other families of the same user are never touched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.familyId = :familyId"
            + " AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
