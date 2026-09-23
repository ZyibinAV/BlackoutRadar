package com.zyibin.app.blackoutradar.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.identity.UserRegistrationService;
import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.ExternalIdentityData;
import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.OAuth2Provider;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.RefreshTokenService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Login integration: email plus password through the existing TASK 29
 * authentication flow issues an access token and opens a fresh rotation
 * family without changing password checking.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class AuthServiceLoginTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenJpaRepository repository;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginIssuesAccessAndRefreshTokens() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        assertEquals(RegistrationResult.CREATED,
                registrationService.register(email, "secret-password"));

        TokenPair pair = authService.login(email, "secret-password");

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        UUID userId = jwtService.parseUserId(pair.accessToken());
        assertNotNull(userId);
        TokenPair rotated = refreshTokenService.refresh(pair.refreshToken());
        assertEquals(userId, jwtService.parseUserId(rotated.accessToken()));
        assertTrueNoLeak(pair);
    }

    @Test
    void eachLoginOpensNewFamily() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        registrationService.register(email, "secret-password");

        TokenPair first = authService.login(email, "secret-password");
        TokenPair second = authService.login(email, "secret-password");

        assertNotEquals(familyOf(first.refreshToken()), familyOf(second.refreshToken()));
        refreshTokenService.refresh(first.refreshToken());
        refreshTokenService.refresh(second.refreshToken());
    }

    @Test
    void wrongPasswordFailsWithoutSession() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        registrationService.register(email, "secret-password");
        long rowsBefore = repository.count();

        assertThrows(BadCredentialsException.class,
                () -> authService.login(email, "wrong-password"));

        assertEquals(rowsBefore, repository.count());
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
    }

    @Test
    void oauthLoginOpensOrdinaryTokenSession() {
        ExternalIdentityData data = new ExternalIdentityData(
                OAuth2Provider.GITHUB,
                "oauth-sub-" + UUID.randomUUID(),
                "oauth-" + UUID.randomUUID() + "@example.com",
                true);

        TokenPair pair = authService.login(data);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        UUID userId = jwtService.parseUserId(pair.accessToken());
        assertNotNull(userId);
        TokenPair rotated = refreshTokenService.refresh(pair.refreshToken());
        assertEquals(userId, jwtService.parseUserId(rotated.accessToken()));
    }

    @Test
    void oauthLoginWithoutUsableEmailFailsWithoutSession() {
        long rowsBefore = repository.count();

        assertThrows(BadCredentialsException.class, () -> authService.login(
                new ExternalIdentityData(OAuth2Provider.VK, "oauth-sub-" + UUID.randomUUID(),
                        null, false)));

        assertEquals(rowsBefore, repository.count());
    }

    @Test
    void loginDoesNotCreateAuthenticatedContextByItself() {        String email = "auth-" + UUID.randomUUID() + "@example.com";
        registrationService.register(email, "secret-password");

        authService.login(email, "secret-password");

        Authentication stored = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(stored == null || !stored.isAuthenticated()
                || !(stored.getPrincipal() instanceof AuthenticatedUser));
    }

    private UUID familyOf(String rawToken) {
        List<RefreshTokenEntity> rows = repository.findAll();
        return rows.stream()
                .filter(row -> row.getTokenHash()
                        .equals(refreshTokenService.hash(rawToken)))
                .findFirst()
                .orElseThrow()
                .getFamilyId();
    }

    private void assertTrueNoLeak(TokenPair pair) {
        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(pair.accessToken().split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
        assertTrue(!payload.contains("password"));
        assertTrue(!payload.contains("refresh"));
    }
}
