package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.RegionalDistrictMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionalDistrictJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegionalDistrictPersistenceAdapter implements RegionalDistrictPort {

    private final RegionalDistrictJpaRepository repository;
    private final RegionalDistrictMapper mapper;

    public RegionalDistrictPersistenceAdapter(RegionalDistrictJpaRepository repository, RegionalDistrictMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegionalDistrict> findByRegionAndTypeAndName(Region region, RegionalDistrictType type, String name) {
        return repository.findByRegionIdAndTypeAndName(region.id(), type, name).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public RegionalDistrict save(RegionalDistrict regionalDistrict) {
        return mapper.toDomain(repository.save(mapper.toEntity(regionalDistrict)));
    }

    @Override
    @Transactional
    public RegionalDistrict resolveCanonical(Region region, RegionalDistrictType type, String canonicalName) {
        repository.insertIfAbsent(java.util.UUID.randomUUID(), region.id(), type.name(), canonicalName);
        return repository.findByRegionIdAndTypeAndName(region.id(), type, canonicalName).map(mapper::toDomain).orElseThrow();
    }
}