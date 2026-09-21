package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Replay detection: reusing a revoked refresh token revokes its whole
 * rotation family while other families of the same user stay usable.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class RefreshTokenReplayTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenJpaRepository repository;

    @Autowired
    private UserPort userPort;

    @Test
    void reuseRevokesWholeFamily() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);
        TokenPair second = refreshTokenService.refresh(first.refreshToken());
        TokenPair third = refreshTokenService.refresh(second.refreshToken());

        BadCredentialsException failure = assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(first.refreshToken()));

        assertEquals("Bad credentials", failure.getMessage());
        List<RefreshTokenEntity> family = repository.findByUserId(userId);
        assertEquals(3, family.size());
        assertTrue(family.stream().allMatch(row -> row.getRevokedAt() != null));
        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(second.refreshToken()));
        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(third.refreshToken()));
    }

    @Test
    void reuseDoesNotTouchOtherFamily() {
        UUID userId = saveUser();
        TokenPair compromised = refreshTokenService.createSession(userId);
        TokenPair rotated = refreshTokenService.refresh(compromised.refreshToken());
        TokenPair other = refreshTokenService.createSession(userId);

        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(compromised.refreshToken()));

        TokenPair next = refreshTokenService.refresh(other.refreshToken());
        assertNotNull(next.accessToken());
        assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(rotated.refreshToken()));
    }

    @Test
    void reuseErrorRevealsNothing() {
        UUID userId = saveUser();
        TokenPair first = refreshTokenService.createSession(userId);
        TokenPair second = refreshTokenService.refresh(first.refreshToken());
        UUID family = repository.findByTokenHash(
                refreshTokenService.hash(first.refreshToken())).orElseThrow().getFamilyId();

        BadCredentialsException failure = assertThrows(BadCredentialsException.class,
                () -> refreshTokenService.refresh(first.refreshToken()));

        String message = failure.getMessage().toLowerCase();
        assertTrue(!message.contains(family.toString().toLowerCase()));
        assertTrue(!message.contains(first.refreshToken().toLowerCase()));
        assertTrue(!message.contains(
                refreshTokenService.hash(second.refreshToken()).toLowerCase()));
    }

    private UUID saveUser() {
        User user = User.of(UUID.randomUUID(),
                "replay-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        return userPort.save(user).id();
    }
}
