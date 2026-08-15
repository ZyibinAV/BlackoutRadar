package com.zyibin.app.blackoutradar.domain.identity;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final String email;
    private final UserRole role;
    private final boolean isActive;
    private final String nickname;
    private final String about;
    private final String avatar;

    private User(UUID id, String email, UserRole role, boolean isActive,
                 String nickname, String about, String avatar) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = DomainPreconditions.requireNotBlank(email, "email must not be blank");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.isActive = isActive;
        this.nickname = nickname;
        this.about = about;
        this.avatar = avatar;
    }

    public static User of(UUID id, String email, UserRole role, boolean isActive) {
        return new User(id, email, role, isActive, null, null, null);
    }

    public static User of(UUID id, String email, UserRole role, boolean isActive,
                          String nickname, String about, String avatar) {
        return new User(id, email, role, isActive, nickname, about, avatar);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public UserRole role() {
        return role;
    }

    public boolean isActive() {
        return isActive;
    }

    public String nickname() {
        return nickname;
    }

    public String about() {
        return about;
    }

    public String avatar() {
        return avatar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{"
                + "id=" + id
                + ", email='" + email + '\''
                + ", role=" + role
                + ", isActive=" + isActive
                + ", nickname='" + nickname + '\''
                + ", about='" + about + '\''
                + ", avatar='" + avatar + '\''
                + '}';
    }
}