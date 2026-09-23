package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Security/Persistence concept linking an internal user to one external
 * OAuth2 identity.
 *
 * <p>This is not a business domain entity: there is no {@code ExternalIdentity}
 * in the domain model (ADR-018). One external identity belongs to exactly one
 * user; one user holds at most one identity per provider. Timestamps are
 * assigned by persistence on creation and may be absent before that.
 */
public record ExternalIdentity(
        UUID id,
        UUID userId,
        OAuth2Provider provider,
        String providerSubject,
        Instant createdAt,
        Instant updatedAt) {

    public ExternalIdentity {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(providerSubject, "providerSubject must not be null");
        if (providerSubject.isBlank()) {
            throw new IllegalArgumentException("providerSubject must not be blank");
        }
    }
}
