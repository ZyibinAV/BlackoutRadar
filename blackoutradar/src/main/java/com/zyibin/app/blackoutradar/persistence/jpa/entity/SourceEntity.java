package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import com.zyibin.app.blackoutradar.persistence.jpa.type.StringJsonbType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "source")
@Getter
@Setter
public class SourceEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Type(StringJsonbType.class)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private String configuration;

    @Column(name = "schedule", nullable = false)
    private String schedule;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;
}