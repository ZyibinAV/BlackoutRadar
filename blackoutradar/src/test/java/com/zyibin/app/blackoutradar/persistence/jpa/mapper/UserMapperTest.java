package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class UserMapperTest {

    @Autowired
    private UserMapper mapper;

    @Test
    void mapsDomainToEntityWithProfileFields() {
        UUID id = UUID.randomUUID();
        User user = User.of(id, "ivan@example.com", UserRole.USER, true, "ivan", "О себе", "avatar-key");

        UserEntity entity = mapper.toEntity(user);

        assertEquals(id, entity.getId());
        assertEquals("ivan@example.com", entity.getEmail());
        assertEquals(UserRole.USER, entity.getRole());
        assertTrue(entity.isActive());
        assertEquals("ivan", entity.getNickname());
        assertEquals("О себе", entity.getAbout());
        assertEquals("avatar-key", entity.getAvatar());
        assertNull(entity.getPasswordHash());
    }

    @Test
    void mapsInactiveAdminWithoutOptionalFields() {
        User user = User.of(UUID.randomUUID(), "admin@example.com", UserRole.ADMIN, false);

        UserEntity entity = mapper.toEntity(user);

        assertEquals(UserRole.ADMIN, entity.getRole());
        assertFalse(entity.isActive());
        assertNull(entity.getNickname());
        assertNull(entity.getAbout());
        assertNull(entity.getAvatar());
        assertNull(entity.getPasswordHash());
    }

    @Test
    void mapsEntityToDomain() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail("ivan@example.com");
        entity.setPasswordHash("hash");
        entity.setRole(UserRole.USER);
        entity.setActive(true);
        entity.setNickname("ivan");
        entity.setAbout("О себе");
        entity.setAvatar("avatar-key");

        User user = mapper.toDomain(entity);

        assertEquals(entity.getId(), user.id());
        assertEquals("ivan@example.com", user.email());
        assertEquals(UserRole.USER, user.role());
        assertTrue(user.isActive());
        assertEquals("ivan", user.nickname());
        assertEquals("О себе", user.about());
        assertEquals("avatar-key", user.avatar());
    }

    @Test
    void roundTripPreservesIdentityAndProfileFields() {
        User original = User.of(UUID.randomUUID(), "ivan@example.com", UserRole.USER, true, "ivan", "О себе", "key");

        User restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.email(), restored.email());
        assertEquals(original.role(), restored.role());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.nickname(), restored.nickname());
        assertEquals(original.about(), restored.about());
        assertEquals(original.avatar(), restored.avatar());
    }

    @Test
    void toDomainDoesNotExposePasswordHash() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail("ivan@example.com");
        entity.setPasswordHash("secret-hash");
        entity.setRole(UserRole.USER);
        entity.setActive(true);
        entity.setNickname("ivan");
        entity.setAbout("О себе");
        entity.setAvatar("avatar-key");

        User user = mapper.toDomain(entity);

        assertEquals("ivan@example.com", user.email());
        assertEquals(UserRole.USER, user.role());
        assertTrue(user.isActive());
        assertEquals("ivan", user.nickname());
        assertEquals("О себе", user.about());
        assertEquals("avatar-key", user.avatar());
        List<String> exposedValues = List.of(user.email(), user.nickname(), user.about(), user.avatar());
        assertFalse(exposedValues.contains("secret-hash"));
    }
}