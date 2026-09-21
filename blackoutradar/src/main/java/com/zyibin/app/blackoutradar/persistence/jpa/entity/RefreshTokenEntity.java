package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Security/persistence representation of an opaque refresh token.
 *
 * <p>This is not a business domain entity: there is no {@code RefreshToken}
 * in the domain model (ADR-009). Only the token hash is stored, never the raw
 * token value. {@code familyId} groups one authentication session rotation
 * chain (ADR-016) and stays in the security/persistence boundary.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
public class RefreshTokenEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
