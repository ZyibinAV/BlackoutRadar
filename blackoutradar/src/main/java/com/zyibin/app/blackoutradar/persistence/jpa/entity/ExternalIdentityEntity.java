package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Security/persistence representation of a link between an internal user
 * and one external OAuth2 identity.
 *
 * <p>This is not a business domain entity: there is no {@code ExternalIdentity}
 * in the domain model (ADR-018). The pair {@code (provider, provider_subject)}
 * identifies the external account; email is intentionally not stored here.
 */
@Entity
@Table(name = "external_identity")
@Getter
@Setter
public class ExternalIdentityEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_subject", nullable = false, updatable = false)
    private String providerSubject;
}
