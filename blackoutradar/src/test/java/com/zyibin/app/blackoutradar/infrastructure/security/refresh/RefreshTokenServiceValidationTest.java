package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RefreshTokenServiceValidationTest {

    @Test
    void invalidLifetimeFailsFast() {
        RefreshTokenJpaRepository repository = Mockito.mock(RefreshTokenJpaRepository.class);
        JwtService jwtService = Mockito.mock(JwtService.class);

        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenService(
                repository, properties(null), jwtService));
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenService(
                repository, properties(Duration.ZERO), jwtService));
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenService(
                repository, properties(Duration.ofDays(-1)), jwtService));
    }

    @Test
    void validLifetimeConstructs() {
        assertDoesNotThrow(() -> new RefreshTokenService(
                Mockito.mock(RefreshTokenJpaRepository.class),
                properties(Duration.ofDays(30)),
                Mockito.mock(JwtService.class)));
    }

    private RefreshTokenProperties properties(Duration lifetime) {
        RefreshTokenProperties properties = new RefreshTokenProperties();
        properties.setLifetime(lifetime);
        return properties;
    }
}
