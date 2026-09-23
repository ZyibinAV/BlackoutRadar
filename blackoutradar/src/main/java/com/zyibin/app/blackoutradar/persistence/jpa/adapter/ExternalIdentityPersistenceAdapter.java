package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.ExternalIdentity;
import com.zyibin.app.blackoutradar.infrastructure.security.oauth2.OAuth2Provider;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.ExternalIdentityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.ExternalIdentityRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for external OAuth2 identities.
 *
 * <p>Exposes only the operations the authentication flow needs: lookup by
 * provider plus subject and creation. Identities are never updated,
 * transferred or deleted through this adapter; there is intentionally no
 * account linking API.
 */
@Component
public class ExternalIdentityPersistenceAdapter {

    private final ExternalIdentityRepository repository;

    public ExternalIdentityPersistenceAdapter(ExternalIdentityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Transactional(readOnly = true)
    public Optional<ExternalIdentity> findByProviderAndSubject(OAuth2Provider provider, String subject) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        return repository.findByProviderAndProviderSubject(provider.name(), subject)
                .map(this::toModel);
    }

    /**
     * Persists a new identity, flushing synchronously so unique constraint
     * violations surface here instead of at transaction commit.
     */
    @Transactional
    public ExternalIdentity save(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        ExternalIdentityEntity entity = new ExternalIdentityEntity();
        entity.setId(identity.id());
        entity.setUserId(identity.userId());
        entity.setProvider(identity.provider().name());
        entity.setProviderSubject(identity.providerSubject());
        return toModel(repository.saveAndFlush(entity));
    }

    private ExternalIdentity toModel(ExternalIdentityEntity entity) {
        return new ExternalIdentity(
                entity.getId(),
                entity.getUserId(),
                OAuth2Provider.valueOf(entity.getProvider()),
                entity.getProviderSubject(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
