package com.zyibin.app.blackoutradar.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class SecurityUserResolverTest {

    @Mock private UserJpaRepository userRepository;

    private SecurityUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SecurityUserResolver(userRepository);
    }

    @Test
    void activeUserResolvesToRoleUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity(userId, UserRole.USER, true)));

        AuthenticatedUser resolved = resolver.resolve(userId);

        assertEquals(userId, resolved.userId());
        assertEquals("user@example.com", resolved.getUsername());
        assertEquals(List.of("ROLE_USER"), authorities(resolved));
        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void activeAdminResolvesToRoleAdminOnly() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity(userId, UserRole.ADMIN, true)));

        AuthenticatedUser resolved = resolver.resolve(userId);

        assertEquals(List.of("ROLE_ADMIN"), authorities(resolved));
        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void missingUserFailsLikeBadCredentials() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> resolver.resolve(userId));
        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void inactiveUserFailsAsDisabled() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity(userId, UserRole.USER, false)));

        assertThrows(DisabledException.class, () -> resolver.resolve(userId));
        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void nullUserIdRejected() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class, () -> new SecurityUserResolver(null));
    }

    private UserEntity entity(UUID userId, UserRole role, boolean active) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setEmail("user@example.com");
        entity.setRole(role);
        entity.setActive(active);
        return entity;
    }

    private List<String> authorities(AuthenticatedUser user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void adminHasNoUserAuthority() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity(userId, UserRole.ADMIN, true)));

        AuthenticatedUser resolved = resolver.resolve(userId);

        assertTrue(authorities(resolved).stream().noneMatch("ROLE_USER"::equals));
    }
}
