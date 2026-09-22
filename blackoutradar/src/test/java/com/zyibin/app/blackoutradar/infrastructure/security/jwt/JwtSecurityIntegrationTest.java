package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Standard Spring Security integration: tokens issued by {@link JwtService}
 * validate through the shared {@link JwtDecoder} bean and convert to
 * authentication with the actual role of the user, while invalid tokens and
 * tokens of missing or inactive users never produce an authenticated token.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class JwtSecurityIntegrationTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtAuthenticationConverter converter;

    @Autowired
    private UserPort userPort;

    @Test
    void decoderBeanValidatesIssuedToken() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        assertEquals(userId.toString(), jwt.getSubject());
    }

    @Test
    void converterResolvesUserRole() {
        UUID userId = saveUser(UserRole.USER, true);
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertTrue(authentication.isAuthenticated());
        assertEquals(userId.toString(), authentication.getName());
        assertEquals(List.of("ROLE_USER"), authorities(authentication));
    }

    @Test
    void converterResolvesAdminRoleOnly() {
        UUID userId = saveUser(UserRole.ADMIN, true);
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertTrue(authentication.isAuthenticated());
        assertEquals(List.of("ROLE_ADMIN"), authorities(authentication));
    }

    @Test
    void converterRejectsMissingUser() {
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(UUID.randomUUID()));

        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt));
    }

    @Test
    void converterRejectsInactiveUser() {
        UUID userId = saveUser(UserRole.USER, false);
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        assertThrows(DisabledException.class, () -> converter.convert(jwt));
    }

    @Test
    void invalidTokenProducesNoAuthentication() {
        String tampered = jwtService.generateAccessToken(UUID.randomUUID()) + "x";

        assertThrows(JwtException.class, () -> {
            Jwt jwt = jwtDecoder.decode(tampered);
            converter.convert(jwt);
        });
    }

    private UUID saveUser(UserRole role, boolean active) {
        User user = User.of(UUID.randomUUID(),
                "jwt-" + UUID.randomUUID() + "@example.com", role, active);
        return userPort.save(user).id();
    }

    private List<String> authorities(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .collect(Collectors.toList());
    }
}
