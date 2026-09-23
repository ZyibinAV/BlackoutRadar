package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import com.zyibin.app.blackoutradar.infrastructure.security.AuthenticatedUser;
import com.zyibin.app.blackoutradar.infrastructure.security.SecurityUserResolver;
import com.zyibin.app.blackoutradar.persistence.jpa.adapter.ExternalIdentityPersistenceAdapter;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Common authentication flow for all OAuth2 providers.
 *
 * <p>Works identically for GitHub and VK: provider-specific responses must
 * already be converted to {@link ExternalIdentityData} before entering.
 * An existing identity resolves its linked user without touching any user
 * data; a new identity is created through {@link ExternalIdentityRegistration}
 * when a usable email is present. Email never links an OAuth2 identity to an
 * existing local user: a taken email is refused before any write, and without
 * a stored identity authentication is securely refused until a separate
 * account-linking flow exists.
 *
 * <p>This service intentionally carries no transaction of its own: the
 * registration attempt runs in an isolated transaction, so after a database
 * constraint conflict the fallback lookup below always executes in fresh
 * transactions instead of continuing inside a rollback-only one.
 */
@Service
public class ExternalIdentityAuthenticationService {

    private static final String BAD_CREDENTIALS_MESSAGE = "Bad credentials";

    private final ExternalIdentityPersistenceAdapter identities;
    private final SecurityUserResolver userResolver;
    private final ExternalIdentityRegistration registration;

    public ExternalIdentityAuthenticationService(
            ExternalIdentityPersistenceAdapter identities,
            SecurityUserResolver userResolver,
            ExternalIdentityRegistration registration) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver must not be null");
        this.registration = Objects.requireNonNull(registration, "registration must not be null");
    }

    /**
     * Authenticates provider-neutral OAuth2 identity data.
     *
     * @throws BadCredentialsException if the identity is unknown and no new
     *     user can be created from it
     */
    public AuthenticatedUser authenticate(ExternalIdentityData data) {
        Objects.requireNonNull(data, "data must not be null");
        return identities.findByProviderAndSubject(data.provider(), data.subject())
                .map(identity -> userResolver.resolve(identity.userId()))
                .orElseGet(() -> registerNew(data));
    }

    private AuthenticatedUser registerNew(ExternalIdentityData data) {
        if (!data.hasUsableEmail()) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        try {
            return registration.register(data);
        } catch (DataIntegrityViolationException conflict) {
            return identities.findByProviderAndSubject(data.provider(), data.subject())
                    .map(identity -> userResolver.resolve(identity.userId()))
                    .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS_MESSAGE));
        }
    }
}
