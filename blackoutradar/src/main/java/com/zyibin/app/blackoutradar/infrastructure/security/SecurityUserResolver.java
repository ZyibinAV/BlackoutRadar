package com.zyibin.app.blackoutradar.infrastructure.security;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the current Security identity of a user by id.
 *
 * <p>The JWT access token carries only {@code sub = User.id} (ADR-015,
 * ADR-017), so the actual user state — existence, activity flag and role —
 * is loaded here through the existing persistence repository. The role to
 * authority mapping itself stays inside {@link AuthenticatedUser} and is
 * never duplicated.
 *
 * <p>A missing user is rejected with the same generic error as unknown
 * credentials, so callers cannot probe account existence. An inactive user
 * is rejected the same way as in {@link LocalAuthenticationProvider}.
 */
@Component
public class SecurityUserResolver {

    private static final String BAD_CREDENTIALS_MESSAGE = "Bad credentials";

    private final UserJpaRepository userRepository;

    public SecurityUserResolver(UserJpaRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    /**
     * Loads the user and returns its active Security identity.
     *
     * @throws BadCredentialsException if no user exists for the given id
     * @throws DisabledException if the user is inactive
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser resolve(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS_MESSAGE));
        if (!entity.isActive()) {
            throw new DisabledException("User is disabled");
        }
        return new AuthenticatedUser(
                entity.getId(), entity.getEmail(), entity.getRole(), entity.isActive());
    }
}
