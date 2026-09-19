package com.zyibin.app.blackoutradar.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock private UserPort userPort;
    @Mock private PasswordEncoder passwordEncoder;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(userPort, passwordEncoder);
    }

    @Test
    void successfulRegistrationHashesPassword() {
        when(passwordEncoder.encode("secret-password")).thenReturn("encoded-hash");
        when(userPort.register(any(User.class), eq("encoded-hash")))
                .thenReturn(RegistrationResult.CREATED);

        RegistrationResult result = service.register("new@example.com", "secret-password");

        assertEquals(RegistrationResult.CREATED, result);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userPort).register(userCaptor.capture(), eq("encoded-hash"));
        User created = userCaptor.getValue();
        assertEquals("new@example.com", created.email());
        assertEquals(UserRole.USER, created.role());
        assertTrue(created.isActive());
        verify(passwordEncoder).encode("secret-password");
    }

    @Test
    void rawPasswordNeverReachesPersistence() {
        when(passwordEncoder.encode("secret-password")).thenReturn("encoded-hash");
        when(userPort.register(any(User.class), any(String.class)))
                .thenReturn(RegistrationResult.CREATED);

        service.register("new@example.com", "secret-password");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userPort).register(any(User.class), hashCaptor.capture());
        assertEquals("encoded-hash", hashCaptor.getValue());
        assertNotEquals("secret-password", hashCaptor.getValue());
    }

    @Test
    void alreadyExistsIsPropagated() {
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-hash");
        when(userPort.register(any(User.class), eq("encoded-hash")))
                .thenReturn(RegistrationResult.ALREADY_EXISTS);

        assertEquals(RegistrationResult.ALREADY_EXISTS, service.register("taken@example.com", "secret-password"));
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> service.register(null, "secret-password"));
        assertThrows(NullPointerException.class, () -> service.register("new@example.com", null));
        verifyNoInteractions(userPort, passwordEncoder);
    }

    @Test
    void blankPasswordRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.register("new@example.com", "   "));
        verifyNoInteractions(userPort, passwordEncoder);
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class, () -> new UserRegistrationService(null, passwordEncoder));
        assertThrows(NullPointerException.class, () -> new UserRegistrationService(userPort, null));
    }
}
