package com.zyibin.app.blackoutradar.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void validCreation() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);

        assertEquals("user@example.com", user.email());
        assertEquals(UserRole.USER, user.role());
        assertTrue(user.isActive());
    }

    @Test
    void validCreationWithProfile() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.ADMIN, false,
                "nick", "about me", "avatar-key");

        assertEquals("nick", user.nickname());
        assertEquals("about me", user.about());
        assertEquals("avatar-key", user.avatar());
        assertFalse(user.isActive());
    }

    @Test
    void profileFieldsOptional() {
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);

        assertNull(user.nickname());
        assertNull(user.about());
        assertNull(user.avatar());
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> User.of(null, "user@example.com", UserRole.USER, true));
    }

    @Test
    void nullEmailRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> User.of(UUID.randomUUID(), null, UserRole.USER, true));
    }

    @Test
    void blankEmailRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> User.of(UUID.randomUUID(), "  ", UserRole.USER, true));
    }

    @Test
    void nullRoleRejected() {
        assertThrows(NullPointerException.class,
                () -> User.of(UUID.randomUUID(), "user@example.com", null, true));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        User a = User.of(id, "user@example.com", UserRole.USER, true);
        User b = User.of(id, "user@example.com", UserRole.USER, true);

        assertNotEquals(a, User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}