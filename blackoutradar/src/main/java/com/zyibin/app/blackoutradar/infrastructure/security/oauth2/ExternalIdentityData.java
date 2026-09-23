package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import java.util.Objects;

/**
 * Provider-neutral representation of an OAuth2 identity.
 *
 * <p>Provider-specific responses are converted to this form before entering
 * the common authentication flow. {@code subject} is the stable user
 * identifier at the provider; email is carried only to create a new user
 * on first login and is never an identity key.
 *
 * @param provider the OAuth2 provider the identity comes from
 * @param subject stable user identifier at the provider, never blank
 * @param email user email as reported by the provider, may be absent
 * @param emailVerified whether the provider marks the email as verified
 */
public record ExternalIdentityData(
        OAuth2Provider provider,
        String subject,
        String email,
        boolean emailVerified) {

    public ExternalIdentityData {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
    }

    /**
     * Email is usable for new user creation only when actually present.
     */
    public boolean hasUsableEmail() {
        return email != null && !email.isBlank();
    }
}
