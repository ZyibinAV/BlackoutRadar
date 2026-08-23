package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.CityMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CityPersistenceAdapter implements CityPort {

    private final CityJpaRepository repository;
    private final CityMapper mapper;

    public CityPersistenceAdapter(CityJpaRepository repository, CityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<City> findByRegionAndName(Region region, String name) {
        return repository.findByRegionIdAndRegionalDistrictIsNullAndName(region.id(), name).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<City> findByRegionAndRegionalDistrictAndName(Region region, RegionalDistrict regionalDistrict, String name) {
        return repository
                .findByRegionIdAndRegionalDistrictIdAndName(region.id(), regionalDistrict.id(), name)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public City save(City city) {
        return mapper.toDomain(repository.save(mapper.toEntity(city)));
    }

    @Override
    @Transactional
    public City resolveCanonicalInRegion(Region region, String canonicalName) {
        repository.insertIfAbsentInRegion(java.util.UUID.randomUUID(), region.id(), canonicalName);
        return repository.findByRegionIdAndRegionalDistrictIsNullAndName(region.id(), canonicalName).map(mapper::toDomain).orElseThrow();
    }

    @Override
    @Transactional
    public City resolveCanonicalInRegionalDistrict(RegionalDistrict regionalDistrict, String canonicalName) {
        repository.insertIfAbsentInRegionalDistrict(java.util.UUID.randomUUID(), regionalDistrict.region().id(), regionalDistrict.id(), canonicalName);
        return repository.findByRegionIdAndRegionalDistrictIdAndName(regionalDistrict.region().id(), regionalDistrict.id(), canonicalName).map(mapper::toDomain).orElseThrow();
    }
}