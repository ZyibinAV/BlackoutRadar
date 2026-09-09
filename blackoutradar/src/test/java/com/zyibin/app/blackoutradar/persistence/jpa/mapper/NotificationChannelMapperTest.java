package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationChannelEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class NotificationChannelMapperTest {

    @Autowired
    private NotificationChannelMapper mapper;

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private UserEntity userEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id());
        entity.setEmail(user.email());
        entity.setRole(user.role());
        entity.setActive(user.isActive());
        return entity;
    }

    @Test
    void mapsDomainToEntity() {
        User user = user();
        UUID id = UUID.randomUUID();
        NotificationChannel channel = NotificationChannel.of(id, user, "email", "personal@example.com", true);

        NotificationChannelEntity entity = mapper.toEntity(channel);

        assertEquals(id, entity.getId());
        assertEquals(user.id(), entity.getUser().getId());
        assertEquals("email", entity.getType());
        assertEquals("personal@example.com", entity.getDestination());
        assertTrue(entity.isEnabled());
    }

    @Test
    void mapsEntityToDomain() {
        User user = user();
        NotificationChannelEntity entity = new NotificationChannelEntity();
        entity.setId(UUID.randomUUID());
        entity.setUser(userEntity(user));
        entity.setType("telegram");
        entity.setDestination("123456789");
        entity.setEnabled(false);

        NotificationChannel channel = mapper.toDomain(entity);

        assertEquals(entity.getId(), channel.id());
        assertEquals(user.id(), channel.user().id());
        assertEquals("telegram", channel.type());
        assertEquals("123456789", channel.destination());
        assertFalse(channel.isEnabled());
    }

    @Test
    void roundTripPreservesData() {
        NotificationChannel original = NotificationChannel.of(UUID.randomUUID(), user(),
                "future-channel", "future-destination", true);

        NotificationChannel restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.user().id(), restored.user().id());
        assertEquals(original.type(), restored.type());
        assertEquals(original.destination(), restored.destination());
        assertEquals(original.isEnabled(), restored.isEnabled());
    }
}
