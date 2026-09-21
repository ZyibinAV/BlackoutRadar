package com.zyibin.app.blackoutradar.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalAuthenticationProviderTest {

    @Mock private UserPort userPort;
    @Mock private PasswordEncoder passwordEncoder;

    private LocalAuthenticationProvider provider;

    private User user;

    @BeforeEach
    void setUp() {
        provider = new LocalAuthenticationProvider(userPort, passwordEncoder);
        user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private Authentication token(String email, String password) {
        return new UsernamePasswordAuthenticationToken(email, password);
    }

    @Test
    void validCredentialsAuthenticate() {
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userPort.findPasswordHash(user.id())).thenReturn(Optional.of("stored-hash"));
        when(passwordEncoder.matches("secret-password", "stored-hash")).thenReturn(true);

        Authentication result = provider.authenticate(token("user@example.com", "secret-password"));

        assertTrue(result.isAuthenticated());
        assertTrue(result.getPrincipal() instanceof AuthenticatedUser);
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) result.getPrincipal();
        assertEquals(user.id(), authenticatedUser.userId());
        assertEquals("user@example.com", authenticatedUser.getUsername());
        assertTrue(authenticatedUser.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
        assertNull(result.getCredentials());
        verify(passwordEncoder).matches("secret-password", "stored-hash");
    }

    @Test
    void authenticatedIdentityCarriesNoPassword() {
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userPort.findPasswordHash(user.id())).thenReturn(Optional.of("stored-hash"));
        when(passwordEncoder.matches("secret-password", "stored-hash")).thenReturn(true);

        Authentication result = provider.authenticate(token("user@example.com", "secret-password"));

        assertTrue(result.getPrincipal() instanceof AuthenticatedUser);
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) result.getPrincipal();
        assertNull(authenticatedUser.getPassword());
        assertNull(result.getCredentials());
    }

    @Test
    void unknownEmailFailsLikeWrongPassword() {
        when(userPort.findByEmail("absent@example.com")).thenReturn(Optional.empty());

        BadCredentialsException unknown =
                assertThrows(BadCredentialsException.class,
                        () -> provider.authenticate(token("absent@example.com", "secret-password")));

        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userPort.findPasswordHash(user.id())).thenReturn(Optional.of("stored-hash"));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        BadCredentialsException wrong =
                assertThrows(BadCredentialsException.class,
                        () -> provider.authenticate(token("user@example.com", "wrong-password")));

        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    void inactiveUserFailsAsDisabled() {
        User inactive = User.of(UUID.randomUUID(), "inactive@example.com", UserRole.USER, false);
        when(userPort.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));

        assertThrows(DisabledException.class,
                () -> provider.authenticate(token("inactive@example.com", "secret-password")));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void missingPasswordHashFails() {
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userPort.findPasswordHash(user.id())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(token("user@example.com", "secret-password")));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void nonStringCredentialsFail() {
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(), "secret-password")));
        verifyNoInteractions(userPort, passwordEncoder);
    }

    @Test
    void supportsUsernamePasswordTokenOnly() {
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
        assertFalse(provider.supports(String.class));
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class, () -> new LocalAuthenticationProvider(null, passwordEncoder));
        assertThrows(NullPointerException.class, () -> new LocalAuthenticationProvider(userPort, null));
    }
}
