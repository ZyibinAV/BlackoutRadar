package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationChannelJpaRepository;
import java.util.List;
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
class NotificationChannelPersistenceTest {

    @Autowired
    private UserPort userPort;

    @Autowired
    private NotificationChannelPort channelPort;

    @Autowired
    private NotificationChannelJpaRepository channelRepository;

    private User saveUser(String email) {
        return userPort.save(User.of(UUID.randomUUID(), email, UserRole.USER, true));
    }

    @Test
    void saveAndFindByIdRoundTrip() {
        User user = saveUser("ivan@example.com");
        UUID id = UUID.randomUUID();
        NotificationChannel channel = NotificationChannel.of(id, user, "email", "personal@example.com", true);

        NotificationChannel saved = channelPort.save(channel);

        assertEquals(id, saved.id());

        Optional<NotificationChannel> found = channelPort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(user.id(), found.get().user().id());
        assertEquals("email", found.get().type());
        assertEquals("personal@example.com", found.get().destination());
        assertTrue(found.get().isEnabled());
    }

    @Test
    void findAbsentIdReturnsEmpty() {
        assertTrue(channelPort.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findByUserIdReturnsOnlyOwnChannels() {
        User user = saveUser("ivan@example.com");
        User other = saveUser("petr@example.com");
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", true));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "work@example.com", false));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "telegram", "123456789", true));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), other, "email", "other@example.com", true));

        List<NotificationChannel> found = channelPort.findByUserId(user.id());

        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(channel -> channel.user().id().equals(user.id())));
        assertTrue(found.stream().anyMatch(channel -> channel.destination().equals("work@example.com")
                && !channel.isEnabled()));
    }

    @Test
    void findByUserIdWithNoChannelsReturnsEmpty() {
        User user = saveUser("ivan@example.com");

        assertTrue(channelPort.findByUserId(user.id()).isEmpty());
    }

    @Test
    void duplicateUserTypeDestinationIsRejected() {
        User user = saveUser("ivan@example.com");
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", true));

        assertThrows(DataIntegrityViolationException.class, () -> {
            channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", false));
            channelRepository.flush();
        });
    }

    @Test
    void sameTypeDifferentDestinationAllowed() {
        User user = saveUser("ivan@example.com");
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", true));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "work@example.com", true));

        assertEquals(2, channelPort.findByUserId(user.id()).size());
    }

    @Test
    void sameTypeDestinationDifferentUserAllowed() {
        User user = saveUser("ivan@example.com");
        User other = saveUser("petr@example.com");
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), user, "email", "same@example.com", true));
        channelPort.save(NotificationChannel.of(UUID.randomUUID(), other, "email", "same@example.com", true));

        assertEquals(1, channelPort.findByUserId(user.id()).size());
        assertEquals(1, channelPort.findByUserId(other.id()).size());
    }
}
