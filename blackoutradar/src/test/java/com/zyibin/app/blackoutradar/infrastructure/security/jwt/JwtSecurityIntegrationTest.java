package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Standard Spring Security integration: tokens issued by {@link JwtService}
 * validate through the shared {@link JwtDecoder} bean and convert to
 * authentication, while invalid tokens never produce an authenticated token.
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

    @Test
    void decoderBeanValidatesIssuedToken() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        assertEquals(userId.toString(), jwt.getSubject());
    }

    @Test
    void converterProducesAuthenticatedToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(userId));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertTrue(authentication.isAuthenticated());
        assertEquals(userId.toString(), authentication.getName());
    }

    @Test
    void invalidTokenProducesNoAuthentication() {
        String tampered = jwtService.generateAccessToken(UUID.randomUUID()) + "x";

        assertThrows(JwtException.class, () -> {
            Jwt jwt = jwtDecoder.decode(tampered);
            converter.convert(jwt);
        });
    }
}
