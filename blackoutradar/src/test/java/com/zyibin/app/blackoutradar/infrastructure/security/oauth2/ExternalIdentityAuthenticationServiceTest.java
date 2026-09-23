package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.identity.UserRegistrationService;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.AuthenticatedUser;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.RefreshTokenService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import com.zyibin.app.blackoutradar.persistence.jpa.adapter.ExternalIdentityPersistenceAdapter;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.ExternalIdentityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.ExternalIdentityRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

/**
 * Integration tests for the common OAuth2 authentication flow:
 * existing identity resolution, new user creation, email rules,
 * no account linking and compatibility with the JWT/Refresh Token model.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class ExternalIdentityAuthenticationServiceTest {

    @Autowired
    private ExternalIdentityAuthenticationService authenticationService;

    @Autowired
    private ExternalIdentityPersistenceAdapter identities;

    @Autowired
    private ExternalIdentityRepository identityRepository;

    @Autowired
    private UserPort userPort;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void existingIdentityResolvesExistingUserWithoutChanges() {
        UUID userId = saveUser("existing-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        identities.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.GITHUB, "sub-existing", null, null));

        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-existing",
                        "different@example.com", false));

        assertEquals(userId, resolved.userId());
        assertEquals(List.of("ROLE_USER"), authorities(resolved));
        assertEquals(1, identityRepository.findAll().stream()
                .filter(row -> "sub-existing".equals(row.getProviderSubject()))
                .count());
    }

    @Test
    void existingAdminIdentityKeepsAdminRole() {
        UUID userId = saveUser("admin-" + UUID.randomUUID() + "@example.com", UserRole.ADMIN, true);
        identities.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.VK, "sub-admin", null, null));

        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.VK, "sub-admin", null, false));

        assertEquals(List.of("ROLE_ADMIN"), authorities(resolved));
    }

    @Test
    void existingIdentityOfInactiveUserRejected() {
        UUID userId = saveUser("inactive-" + UUID.randomUUID() + "@example.com", UserRole.USER, false);
        identities.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.GITHUB, "sub-inactive", null, null));

        assertThrows(DisabledException.class, () -> authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-inactive",
                        "inactive@example.com", true)));
    }

    @Test
    void newIdentityWithUsableEmailCreatesUserAndIdentityAtomically() {
        String email = "first-" + UUID.randomUUID() + "@example.com";

        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-first", email, true));

        assertNotNull(resolved.userId());
        assertEquals(List.of("ROLE_USER"), authorities(resolved));
        assertTrue(resolved.isEnabled());
        assertEquals(resolved.userId(), userRepository.findByEmail(email).orElseThrow().getId());
        ExternalIdentityEntity identity = identityRepository
                .findByProviderAndProviderSubject("GITHUB", "sub-first").orElseThrow();
        assertEquals(resolved.userId(), identity.getUserId());
        assertNotNull(identity.getCreatedAt());
    }

    @Test
    void newOAuthUserHasNoLocalPassword() {
        String email = "nopass-" + UUID.randomUUID() + "@example.com";

        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.VK, "sub-nopass", email, false));

        assertNull(userRepository.findById(resolved.userId()).orElseThrow().getPasswordHash());
    }

    @Test
    void newIdentityWithoutEmailRejectedWithoutCreations() {
        long usersBefore = userRepository.count();
        long identitiesBefore = identityRepository.count();

        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.VK, "sub-noemail", null, false)));
        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.VK, "sub-blank", "  ", false)));

        assertEquals(usersBefore, userRepository.count());
        assertEquals(identitiesBefore, identityRepository.count());
        assertTrue(identityRepository
                .findByProviderAndProviderSubject("VK", "sub-noemail").isEmpty());
    }

    @Test
    void matchingLocalEmailDoesNotAutoLink() {
        String email = "local-" + UUID.randomUUID() + "@example.com";
        registrationService.register(email, "secret-password");
        long usersBefore = userRepository.count();

        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-nolink", email, true)));

        assertEquals(usersBefore, userRepository.count());
        assertTrue(identityRepository
                .findByProviderAndProviderSubject("GITHUB", "sub-nolink").isEmpty());
    }

    @Test
    void identityCannotBeTransferredToAnotherUser() {
        UUID firstUser = saveUser("first-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        saveUser("second-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        identities.save(new ExternalIdentity(
                UUID.randomUUID(), firstUser, OAuth2Provider.GITHUB, "sub-owned", null, null));

        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-owned",
                        "second@example.com", true));

        assertEquals(firstUser, resolved.userId());
        assertEquals(firstUser, identityRepository
                .findByProviderAndProviderSubject("GITHUB", "sub-owned").orElseThrow().getUserId());
    }

    @Test
    void repeatedLoginCreatesNothingNew() {
        ExternalIdentityData data = new ExternalIdentityData(
                OAuth2Provider.GITHUB, "sub-repeat-" + UUID.randomUUID(),
                "repeat-" + UUID.randomUUID() + "@example.com", true);

        AuthenticatedUser first = authenticationService.authenticate(data);
        AuthenticatedUser second = authenticationService.authenticate(data);

        assertEquals(first.userId(), second.userId());
        assertEquals(first.userId(),
                userRepository.findByEmail(data.email()).orElseThrow().getId());
        assertEquals(1, identityRepository.findAll().stream()
                .filter(row -> data.subject().equals(row.getProviderSubject()))
                .count());
    }

    @Test
    void resolvedUserIsCompatibleWithJwtAndRefreshToken() {
        AuthenticatedUser resolved = authenticationService.authenticate(
                new ExternalIdentityData(OAuth2Provider.GITHUB, "sub-compat-" + UUID.randomUUID(),
                        "compat-" + UUID.randomUUID() + "@example.com", true));

        TokenPair pair = refreshTokenService.createSession(resolved.userId());

        assertEquals(resolved.userId(), jwtService.parseUserId(pair.accessToken()));
        TokenPair rotated = refreshTokenService.refresh(pair.refreshToken());
        assertEquals(resolved.userId(), jwtService.parseUserId(rotated.accessToken()));
    }

    @Test
    void nullDataRejected() {
        assertThrows(NullPointerException.class, () -> authenticationService.authenticate(null));
    }

    @Test
    void concurrentFirstLoginCreatesSingleUserAndIdentity() throws Exception {
        ExternalIdentityData data = new ExternalIdentityData(
                OAuth2Provider.GITHUB,
                "sub-race-" + UUID.randomUUID(),
                "race-" + UUID.randomUUID() + "@example.com",
                true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<AuthenticatedUser> left = executor.submit(() -> attempt(data, start));
            Future<AuthenticatedUser> right = executor.submit(() -> attempt(data, start));
            start.countDown();

            AuthenticatedUser leftResult = left.get(30, TimeUnit.SECONDS);
            AuthenticatedUser rightResult = right.get(30, TimeUnit.SECONDS);

            assertEquals(leftResult.userId(), rightResult.userId());
            assertEquals(leftResult.userId(),
                    userRepository.findByEmail(data.email()).orElseThrow().getId());
            assertEquals(1, identityRepository.findAll().stream()
                    .filter(row -> data.subject().equals(row.getProviderSubject()))
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }

    private AuthenticatedUser attempt(ExternalIdentityData data, CountDownLatch start) throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return authenticationService.authenticate(data);
    }

    private UUID saveUser(String email, UserRole role, boolean active) {
        return userPort.save(User.of(UUID.randomUUID(), email, role, active)).id();
    }

    private List<String> authorities(AuthenticatedUser user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .collect(Collectors.toList());
    }
}
