package com.zyibin.app.blackoutradar.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    @Test
    void validCreation() {
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user(), NOW.plusSeconds(3600));

        assertEquals(NOW.plusSeconds(3600), token.expiresAt());
        assertNull(token.revokedAt());
    }

    @Test
    void belongsToUser() {
        User user = user();
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user, NOW.plusSeconds(3600));

        assertEquals(user, token.user());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> RefreshToken.of(null, user(), NOW.plusSeconds(3600)));
    }

    @Test
    void nullUserRejected() {
        assertThrows(NullPointerException.class,
                () -> RefreshToken.of(UUID.randomUUID(), null, NOW.plusSeconds(3600)));
    }

    @Test
    void nullExpiresAtRejected() {
        assertThrows(NullPointerException.class,
                () -> RefreshToken.of(UUID.randomUUID(), user(), null));
    }

    @Test
    void validTokenIsUsable() {
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user(), NOW.plusSeconds(3600));

        assertTrue(token.isUsable(NOW));
        assertFalse(token.isExpired(NOW));
        assertFalse(token.isRevoked());
    }

    @Test
    void expiredTokenIsNotUsable() {
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user(), NOW.minusSeconds(1));

        assertTrue(token.isExpired(NOW));
        assertFalse(token.isUsable(NOW));
    }

    @Test
    void expiresAtInstantIsExpired() {
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user(), NOW);

        assertTrue(token.isExpired(NOW));
        assertFalse(token.isUsable(NOW));
    }

    @Test
    void revokedTokenIsNotUsable() {
        RefreshToken token = RefreshToken.of(UUID.randomUUID(), user(),
                NOW.plusSeconds(3600), NOW);

        assertTrue(token.isRevoked());
        assertFalse(token.isUsable(NOW));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        User user = user();
        RefreshToken a = RefreshToken.of(id, user, NOW.plusSeconds(3600));
        RefreshToken b = RefreshToken.of(id, user, NOW.plusSeconds(3600));

        assertNotEquals(a, RefreshToken.of(UUID.randomUUID(), user, NOW.plusSeconds(3600)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}