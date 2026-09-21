package com.zyibin.app.blackoutradar.infrastructure.security;

import com.zyibin.app.blackoutradar.infrastructure.security.refresh.RefreshTokenService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Login integration for local authentication (TASK 29 + TASK 30).
 *
 * <p>Flow: email plus password through {@link AuthenticationManager} and the
 * existing {@link LocalAuthenticationProvider} (password checking unchanged),
 * then a new access token plus the first refresh token of a fresh rotation
 * family. Every login starts its own family, so sessions stay isolated.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService) {
        this.authenticationManager =
                Objects.requireNonNull(authenticationManager, "authenticationManager must not be null");
        this.refreshTokenService =
                Objects.requireNonNull(refreshTokenService, "refreshTokenService must not be null");
    }

    /**
     * Authenticates with email plus password and opens a new token session.
     */
    public TokenPair login(String email, String rawPassword) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, rawPassword));
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return refreshTokenService.createSession(principal.userId());
    }
}
