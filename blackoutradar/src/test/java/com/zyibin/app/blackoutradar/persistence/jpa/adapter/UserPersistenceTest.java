package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserPersistenceTest {

    @Autowired
    private UserPort userPort;

    @Autowired
    private UserJpaRepository userRepository;

    @Test
    void saveAndFindByEmailRoundTrip() {
        UUID id = UUID.randomUUID();
        User user = User.of(id, "ivan@example.com", UserRole.USER, true, "ivan", "О себе", "avatar-key");

        User saved = userPort.save(user);

        assertEquals(id, saved.id());

        Optional<User> found = userPort.findByEmail("ivan@example.com");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals("ivan@example.com", found.get().email());
        assertEquals(UserRole.USER, found.get().role());
        assertTrue(found.get().isActive());
        assertEquals("ivan", found.get().nickname());
        assertEquals("О себе", found.get().about());
        assertEquals("avatar-key", found.get().avatar());
    }

    @Test
    void saveWithoutOptionalFieldsRoundTrip() {
        UUID id = UUID.randomUUID();

        userPort.save(User.of(id, "admin@example.com", UserRole.ADMIN, false));

        Optional<User> found = userPort.findByEmail("admin@example.com");

        assertTrue(found.isPresent());
        assertEquals(UserRole.ADMIN, found.get().role());
        assertTrue(!found.get().isActive());
        assertTrue(found.get().nickname() == null);
        assertTrue(found.get().about() == null);
        assertTrue(found.get().avatar() == null);
    }

    @Test
    void findAbsentEmailReturnsEmpty() {
        assertTrue(userPort.findByEmail("absent@example.com").isEmpty());
    }

    @Test
    void duplicateEmailIsRejected() {
        userPort.save(User.of(UUID.randomUUID(), "ivan@example.com", UserRole.USER, true));

        assertThrows(DataIntegrityViolationException.class, () -> {
            userPort.save(User.of(UUID.randomUUID(), "ivan@example.com", UserRole.USER, true));
            userRepository.flush();
        });
    }

    @Test
    void saveDoesNotWipeExistingPasswordHash() {
        UUID id = UUID.randomUUID();
        userPort.save(User.of(id, "ivan@example.com", UserRole.USER, true));

        UserEntity managed = userRepository.findById(id).orElseThrow();
        managed.setPasswordHash("bcrypt-hash");
        userRepository.flush();

        userPort.save(User.of(id, "ivan@example.com", UserRole.USER, true));

        UserEntity persisted = userRepository.findById(id).orElseThrow();
        assertEquals("bcrypt-hash", persisted.getPasswordHash());
    }
}