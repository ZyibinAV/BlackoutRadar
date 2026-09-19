package com.zyibin.app.blackoutradar.application.identity;

import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local registration use case: email plus raw password.
 *
 * <p>The raw password never reaches the domain or persistence layers: it is
 * hashed here and only the hash is passed to {@code UserPort.register(...)}.
 */
@Service
public class UserRegistrationService {

    private final UserPort userPort;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserPort userPort, PasswordEncoder passwordEncoder) {
        this.userPort = Objects.requireNonNull(userPort, "userPort must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    @Transactional
    public RegistrationResult register(String email, String rawPassword) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
        User user = User.of(UUID.randomUUID(), email, UserRole.USER, true);
        String passwordHash = passwordEncoder.encode(rawPassword);
        return userPort.register(user, passwordHash);
    }
}
