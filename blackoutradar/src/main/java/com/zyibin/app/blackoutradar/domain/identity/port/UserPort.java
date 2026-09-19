package com.zyibin.app.blackoutradar.domain.identity.port;

import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import java.util.Optional;
import java.util.UUID;

public interface UserPort {

    Optional<User> findByEmail(String email);

    User save(User user);

    /**
     * Returns the stored password hash without exposing it through {@code User}.
     */
    Optional<String> findPasswordHash(UUID userId);

    /**
     * Atomically creates the user together with its password hash.
     * Returns {@code ALREADY_EXISTS} when the email is already taken instead of
     * throwing a uniqueness violation for the regular concurrent case.
     */
    RegistrationResult register(User user, String passwordHash);
}
