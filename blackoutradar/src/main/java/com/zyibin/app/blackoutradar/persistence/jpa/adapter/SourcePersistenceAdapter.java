package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.SourceMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SourceJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SourcePersistenceAdapter implements SourcePort {

    private final SourceJpaRepository repository;
    private final SourceMapper mapper;

    public SourcePersistenceAdapter(SourceJpaRepository repository, SourceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Source> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Source save(Source source) {
        SourceEntity saved = repository.save(mapper.toEntity(source));
        repository.flush();
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Source> findAllActive() {
        return repository.findByIsActiveTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }
}