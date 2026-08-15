package com.zyibin.app.blackoutradar.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RefreshToken {

    private final UUID id;
    private final User user;
    private final Instant expiresAt;
    private final Instant revokedAt;

    private RefreshToken(UUID id, User user, Instant expiresAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.revokedAt = revokedAt;
    }

    public static RefreshToken of(UUID id, User user, Instant expiresAt) {
        return new RefreshToken(id, user, expiresAt, null);
    }

    public static RefreshToken of(UUID id, User user, Instant expiresAt, Instant revokedAt) {
        return new RefreshToken(id, user, expiresAt, revokedAt);
    }

    public UUID id() {
        return id;
    }

    public User user() {
        return user;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshToken that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RefreshToken{"
                + "id=" + id
                + ", user=" + user
                + ", expiresAt=" + expiresAt
                + ", revokedAt=" + revokedAt
                + '}';
    }
}