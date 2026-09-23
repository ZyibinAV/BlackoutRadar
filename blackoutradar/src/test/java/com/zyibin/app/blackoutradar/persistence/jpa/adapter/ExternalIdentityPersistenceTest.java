package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.ExternalIdentity;
import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.OAuth2Provider;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.ExternalIdentityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.ExternalIdentityRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ExternalIdentityPersistenceTest {

    @Autowired
    private ExternalIdentityPersistenceAdapter adapter;

    @Autowired
    private ExternalIdentityRepository repository;

    @Autowired
    private UserPort userPort;

    @Test
    void createAndFindByProviderAndSubject() {
        UUID userId = saveUser();
        ExternalIdentity identity = new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.GITHUB, "83964673", null, null);

        ExternalIdentity saved = adapter.save(identity);

        assertEquals(identity.id(), saved.id());
        assertEquals(userId, saved.userId());
        assertEquals(OAuth2Provider.GITHUB, saved.provider());
        assertEquals("83964673", saved.providerSubject());
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());

        ExternalIdentity found = adapter
                .findByProviderAndSubject(OAuth2Provider.GITHUB, "83964673")
                .orElseThrow();
        assertEquals(saved, found);
    }

    @Test
    void unknownIdentityReturnsEmpty() {
        assertTrue(adapter
                .findByProviderAndSubject(OAuth2Provider.VK, "no-such-subject")
                .isEmpty());
    }

    @Test
    void duplicateProviderAndSubjectRejected() {
        UUID firstUser = saveUser();
        UUID secondUser = saveUser();
        adapter.save(new ExternalIdentity(
                UUID.randomUUID(), firstUser, OAuth2Provider.GITHUB, "83964673", null, null));

        ExternalIdentityEntity duplicate = newEntity(
                secondUser, OAuth2Provider.GITHUB, "83964673");

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void secondIdentityOfSameProviderForOneUserRejected() {
        UUID userId = saveUser();
        adapter.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.VK, "vk-sub-1", null, null));

        ExternalIdentityEntity duplicate =
                newEntity(userId, OAuth2Provider.VK, "vk-sub-2");

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void sameSubjectOfDifferentProviderAllowed() {
        UUID userId = saveUser();
        adapter.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.GITHUB, "shared-subject", null, null));

        ExternalIdentity second = adapter.save(new ExternalIdentity(
                UUID.randomUUID(), userId, OAuth2Provider.VK, "shared-subject", null, null));

        assertEquals(OAuth2Provider.VK, second.provider());
        assertTrue(adapter
                .findByProviderAndSubject(OAuth2Provider.VK, "shared-subject")
                .isPresent());
    }

    @Test
    void unknownUserRejectedByForeignKey() {
        ExternalIdentityEntity orphan = newEntity(
                UUID.randomUUID(), OAuth2Provider.GITHUB, "83964673");

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(orphan));
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class,
                () -> adapter.findByProviderAndSubject(null, "83964673"));
        assertThrows(NullPointerException.class,
                () -> adapter.findByProviderAndSubject(OAuth2Provider.GITHUB, null));
        assertThrows(NullPointerException.class, () -> adapter.save(null));
    }

    private UUID saveUser() {
        User user = User.of(UUID.randomUUID(),
                "oauth-" + UUID.randomUUID() + "@example.com", UserRole.USER, true);
        return userPort.save(user).id();
    }

    private ExternalIdentityEntity newEntity(UUID userId, OAuth2Provider provider, String subject) {
        ExternalIdentityEntity entity = new ExternalIdentityEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setProvider(provider.name());
        entity.setProviderSubject(subject);
        return entity;
    }
}
