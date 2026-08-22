package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.TransformerStationMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.TransformerStationJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransformerStationPersistenceAdapter implements TransformerStationPort {

    private final TransformerStationJpaRepository repository;
    private final TransformerStationMapper mapper;

    public TransformerStationPersistenceAdapter(TransformerStationJpaRepository repository,
                                                TransformerStationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransformerStation> findByName(String name) {
        return repository.findByName(name).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public TransformerStation save(TransformerStation station) {
        return mapper.toDomain(repository.save(mapper.toEntity(station)));
    }
}