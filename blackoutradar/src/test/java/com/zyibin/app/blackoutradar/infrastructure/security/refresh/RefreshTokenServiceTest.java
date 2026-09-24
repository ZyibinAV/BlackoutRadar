package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Integration tests for refresh token issuance, hash-only persistence,
 * expiration, normal rotation and session isolation.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenJpaRepository repository;

    @Autowired
    private UserPort userPort;

    @Autowired
    private JwtService jwtService;

    @Test
    void createSessionPersistsOnlyHash() {
        UUID userId = saveUser();

        TokenPair pair = refreshTokenService.createSession(userId);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertEquals(userId, jwtService.parseUserId(pair.accessToken()));
        List<RefreshTokenEntity> rows = repository.findByUserId(userId);
        assertEquals(1, rows.size());
        RefreshTokenEntity row = rows.get(0);
        assertEquals(refreshTokenService.hash(pair.refreshToken()), row.getTokenHash());
        assertNotEquals(pair.refreshToken(), row.getTokenHash());
        assertNotNull(row.getFamilyId());
        assertNull(row.getRevokedAt());
        assertTrue(row.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void normalRotationRevokesOldAndKeepsFamily() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);
        UUID family = familyOf(first.refreshToken());

        TokenPair second = refreshTokenService.refresh(first.refreshToken());

        assertNotNull(second.accessToken());
        assertNotNull(second.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertEquals(userId, jwtService.parseUserId(second.accessToken()));
        assertNotNull(repository.findByTokenHash(
                refreshTokenService.hash(first.refreshToken())).orElseThrow().getRevokedAt());
        assertEquals(family, familyOf(second.refreshToken()));
    }

    @Test
    void rotationChainKeepsSingleFamily() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);

        TokenPair second = refreshTokenService.refresh(first.refreshToken());
        TokenPair third = refreshTokenService.refresh(second.refreshToken());

        assertEquals(familyOf(first.refreshToken()), familyOf(second.refreshToken()));
        assertEquals(familyOf(first.refreshToken()), familyOf(third.refreshToken()));
        assertEquals(3, repository.findByUserId(userId).size());
    }

    @Test
    void expiredTokenCannotRotate() {
        UUID userId = saveUser();
        String raw = "expired-raw-" + UUID.randomUUID();
        RefreshTokenEntity expired = new RefreshTokenEntity();
        expired.setId(UUID.randomUUID());
        expired.setUserId(userId);
        expired.setFamilyId(UUID.randomUUID());
        expired.setTokenHash(refreshTokenService.hash(raw));
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        repository.saveAndFlush(expired);

        BadCredentialsException failure =
                assertThrows(BadCredentialsException.class,
                        () -> refreshTokenService.refresh(raw));

        assertEquals("Bad credentials", failure.getMessage());
        assertTrue(!failure.getMessage().contains(raw));
        assertTrue(!failure.getMessage().contains(refreshTokenService.hash(raw)));
        assertEquals(1, repository.findByUserId(userId).size());
    }

    @Test
    void revokedAndExpiredTokenStillTriggersFamilyRevocation() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);
        TokenPair second = refreshTokenService.refresh(first.refreshToken());
        RefreshTokenEntity revoked = repository.findByTokenHash(
                refreshTokenService.hash(first.refreshToken())).orElseThrow();
        revoked.setExpiresAt(Instant.now().minusSeconds(60));
        repository.saveAndFlush(revoked);

        BadCredentialsException failure =
                assertThrows(BadCredentialsException.class,
                        () -> refreshTokenService.refresh(first.refreshToken()));

        assertEquals("Bad credentials", failure.getMessage());
        List<RefreshTokenEntity> family = repository.findByUserId(userId);
        assertEquals(2, family.size());
        assertTrue(family.stream().allMatch(row -> row.getRevokedAt() != null));
        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(second.refreshToken()));
    }

    @Test
    void unknownTokenFailsGenerically() {
        String raw = "unknown-" + UUID.randomUUID();
        BadCredentialsException failure =
                assertThrows(BadCredentialsException.class,
                        () -> refreshTokenService.refresh(raw));

        assertEquals("Bad credentials", failure.getMessage());
        assertTrue(!failure.getMessage().contains(raw));
    }

    @Test
    void blankTokenFailsGenerically() {
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.refresh(null));
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.refresh("  "));
    }

    @Test
    void newSessionGetsNewFamily() {
        UUID userId = saveUser();

        TokenPair first = refreshTokenService.createSession(userId);
        TokenPair second = refreshTokenService.createSession(userId);

        assertNotEquals(familyOf(first.refreshToken()), familyOf(second.refreshToken()));
        assertEquals(2, repository.findByUserId(userId).size());
    }

    @Test
    void logoutRevokesOwnFamilyAndKeepsOther() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);
        TokenPair second = refreshTokenService.createSession(userId);

        refreshTokenService.logout(first.refreshToken());

        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(first.refreshToken()));
        TokenPair rotated = refreshTokenService.refresh(second.refreshToken());
        assertNotNull(rotated.accessToken());
    }

    @Test
    void logoutWithUnknownTokenIsSilent() {
        refreshTokenService.logout("unknown-" + UUID.randomUUID());
        refreshTokenService.logout(null);
        refreshTokenService.logout("  ");
    }

    private UUID saveUser() {
        User user = User.of(UUID.randomUUID(),
                "refresh-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        return userPort.save(user).id();
    }

    private UUID familyOf(String rawToken) {
        return repository.findByTokenHash(refreshTokenService.hash(rawToken))
                .orElseThrow().getFamilyId();
    }
}
