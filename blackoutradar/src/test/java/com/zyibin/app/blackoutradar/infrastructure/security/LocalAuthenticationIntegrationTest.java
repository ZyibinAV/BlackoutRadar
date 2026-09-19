package com.zyibin.app.blackoutradar.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.identity.UserRegistrationService;
import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LocalAuthenticationIntegrationTest {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void registerThenLoginPopulatesSecurityContext() {
        String email = "login-" + UUID.randomUUID() + "@example.com";

        assertEquals(RegistrationResult.CREATED, registrationService.register(email, "secret-password"));
        assertEquals(RegistrationResult.ALREADY_EXISTS, registrationService.register(email, "other-password"));

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "secret-password"));

        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getPrincipal() instanceof AuthenticatedUser);
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals(email, authenticatedUser.getUsername());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            Authentication stored = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(stored.isAuthenticated());
            assertTrue(stored.getPrincipal() instanceof AuthenticatedUser);
            assertEquals(email, ((AuthenticatedUser) stored.getPrincipal()).getUsername());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void wrongPasswordFailsAuthentication() {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        registrationService.register(email, "secret-password");

        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "wrong-password")));
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
    }
}
