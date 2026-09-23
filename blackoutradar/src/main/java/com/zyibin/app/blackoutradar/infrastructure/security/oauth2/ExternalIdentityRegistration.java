package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.AuthenticatedUser;
import com.zyibin.app.blackoutradar.infrastructure.security.SecurityUserResolver;
import com.zyibin.app.blackoutradar.persistence.jpa.adapter.ExternalIdentityPersistenceAdapter;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single registration attempt for a new OAuth2 identity: creates the user
 * and its identity as one atomic unit.
 *
 * <p>Runs in its own transaction ({@code REQUIRES_NEW}), separate from any
 * caller: on a database constraint conflict the whole attempt rolls back
 * cleanly, so the caller can look up the winning identity in fresh
 * transactions instead of continuing inside a broken one. The identity is
 * re-checked inside the attempt so a concurrent winner is resolved rather
 * than duplicated.
 */
@Service
public class ExternalIdentityRegistration {

    private final ExternalIdentityPersistenceAdapter identities;
    private final UserPort users;
    private final SecurityUserResolver userResolver;

    public ExternalIdentityRegistration(
            ExternalIdentityPersistenceAdapter identities,
            UserPort users,
            SecurityUserResolver userResolver) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthenticatedUser register(ExternalIdentityData data) {
        Objects.requireNonNull(data, "data must not be null");
        return identities.findByProviderAndSubject(data.provider(), data.subject())
                .map(identity -> userResolver.resolve(identity.userId()))
                .orElseGet(() -> createNew(data));
    }

    private AuthenticatedUser createNew(ExternalIdentityData data) {
        User user = users.save(User.of(UUID.randomUUID(), data.email(), UserRole.USER, true));
        identities.save(new ExternalIdentity(
                UUID.randomUUID(), user.id(), data.provider(), data.subject(), null, null));
        return userResolver.resolve(user.id());
    }
}
