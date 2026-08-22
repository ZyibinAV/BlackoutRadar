package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import com.zyibin.app.blackoutradar.domain.address.StreetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "street")
@Getter
@Setter
public class StreetEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false, updatable = false)
    private CityEntity city;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private StreetType type;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;
}