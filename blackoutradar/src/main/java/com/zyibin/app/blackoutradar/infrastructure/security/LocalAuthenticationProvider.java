package com.zyibin.app.blackoutradar.infrastructure.security;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local email plus password authentication against stored password hashes.
 *
 * <p>Unknown email and wrong password fail with the identical error so that
 * callers cannot probe account existence. Inactive accounts fail as disabled.
 */
@Component
public class LocalAuthenticationProvider implements AuthenticationProvider {

    private static final String BAD_CREDENTIALS_MESSAGE = "Bad credentials";

    private final UserPort userPort;
    private final PasswordEncoder passwordEncoder;

    public LocalAuthenticationProvider(UserPort userPort, PasswordEncoder passwordEncoder) {
        this.userPort = Objects.requireNonNull(userPort, "userPort must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        Object credentials = authentication.getCredentials();
        if (!(principal instanceof String email) || !(credentials instanceof String password)) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        User user = userPort.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS_MESSAGE));
        if (!user.isActive()) {
            throw new DisabledException("User is disabled");
        }
        String passwordHash = userPort.findPasswordHash(user.id())
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS_MESSAGE));
        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        AuthenticatedUser authenticatedUser =
                new AuthenticatedUser(user.id(), user.email(), user.role(), user.isActive());
        return new UsernamePasswordAuthenticationToken(
                authenticatedUser, null, authenticatedUser.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
