package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.StreetMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.StreetJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StreetPersistenceAdapter implements StreetPort {

    private final StreetJpaRepository repository;
    private final StreetMapper mapper;

    public StreetPersistenceAdapter(StreetJpaRepository repository, StreetMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Street> findByCityAndTypeAndCanonicalName(City city, StreetType type, String canonicalName) {
        return repository.findByCityIdAndTypeAndCanonicalName(city.id(), type, canonicalName).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Street save(Street street) {
        return mapper.toDomain(repository.save(mapper.toEntity(street)));
    }

    @Override
    @Transactional
    public Street resolveCanonical(City city, StreetType type, String canonicalName) {
        repository.insertIfAbsent(java.util.UUID.randomUUID(), city.id(), type.name(), canonicalName);
        return repository.findByCityIdAndTypeAndCanonicalName(city.id(), type, canonicalName).map(mapper::toDomain).orElseThrow();
    }
}