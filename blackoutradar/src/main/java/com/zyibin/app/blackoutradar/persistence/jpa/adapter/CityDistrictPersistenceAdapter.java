package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.CityDistrictMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityDistrictJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CityDistrictPersistenceAdapter implements CityDistrictPort {

    private final CityDistrictJpaRepository repository;
    private final CityDistrictMapper mapper;

    public CityDistrictPersistenceAdapter(CityDistrictJpaRepository repository, CityDistrictMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CityDistrict> findByCityAndName(City city, String name) {
        return repository.findByCityIdAndName(city.id(), name).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public CityDistrict save(CityDistrict cityDistrict) {
        return mapper.toDomain(repository.save(mapper.toEntity(cityDistrict)));
    }

    @Override
    @Transactional
    public CityDistrict resolveCanonical(City city, String canonicalName) {
        repository.insertIfAbsent(java.util.UUID.randomUUID(), city.id(), canonicalName);
        return repository.findByCityIdAndName(city.id(), canonicalName).map(mapper::toDomain).orElseThrow();
    }
}