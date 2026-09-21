package com.zyibin.app.blackoutradar.infrastructure.security;

import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security representation of an authenticated user.
 * Lives outside the domain layer; the domain {@code User} knows nothing
 * about Spring Security and never carries credentials.
 *
 * <p>The identity never stores a password: password verification happens
 * against the stored hash before this object is created, and the resulting
 * authentication carries no password credentials. {@link #getPassword()}
 * therefore returns {@code null} to represent absent credentials rather
 * than a fictitious value.
 */
public final class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final String email;
    private final UserRole role;
    private final boolean active;

    public AuthenticatedUser(UUID userId, String email, UserRole role, boolean active) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.active = active;
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{"
                + "userId=" + userId
                + ", email='" + email + '\''
                + ", role=" + role
                + ", active=" + active
                + '}';
    }
}
