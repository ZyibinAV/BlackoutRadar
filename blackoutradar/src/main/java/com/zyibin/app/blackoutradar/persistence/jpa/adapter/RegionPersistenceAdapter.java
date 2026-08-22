package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.RegionMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegionPersistenceAdapter implements RegionPort {

    private final RegionJpaRepository repository;
    private final RegionMapper mapper;

    public RegionPersistenceAdapter(RegionJpaRepository repository, RegionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Region> findByName(String name) {
        return repository.findByName(name).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Region save(Region region) {
        return mapper.toDomain(repository.save(mapper.toEntity(region)));
    }
}