package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "address")
@Getter
@Setter
public class AddressEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false, updatable = false)
    private CityEntity city;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "street_id", nullable = false, updatable = false)
    private StreetEntity street;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_district_id")
    private CityDistrictEntity cityDistrict;

    @Column(name = "house_number", nullable = false)
    private String houseNumber;

    @Column(name = "house_addition")
    private String houseAddition;

    @Column(name = "canonical_house", nullable = false)
    private String canonicalHouse;
}