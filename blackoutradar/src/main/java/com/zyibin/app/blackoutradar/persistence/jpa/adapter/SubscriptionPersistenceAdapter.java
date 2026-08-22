package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionTransformerStationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.SubscriptionMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.TransformerStationMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionTransformerStationJpaRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionPersistenceAdapter implements SubscriptionPort {

    private final SubscriptionJpaRepository repository;
    private final SubscriptionTransformerStationJpaRepository stationAssociationRepository;
    private final SubscriptionMapper mapper;
    private final TransformerStationMapper transformerStationMapper;

    public SubscriptionPersistenceAdapter(SubscriptionJpaRepository repository,
                                          SubscriptionTransformerStationJpaRepository stationAssociationRepository,
                                          SubscriptionMapper mapper,
                                          TransformerStationMapper transformerStationMapper) {
        this.repository = repository;
        this.stationAssociationRepository = stationAssociationRepository;
        this.mapper = mapper;
        this.transformerStationMapper = transformerStationMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public Subscription save(Subscription subscription) {
        SubscriptionEntity entity = mapper.toEntity(subscription);
        SubscriptionEntity saved = repository.save(entity);
        stationAssociationRepository.deleteBySubscriptionId(subscription.id());
        if (subscription.hasTransformerStations()) {
            stationAssociationRepository.saveAll(buildAssociations(saved, subscription.transformerStations()));
        }
        repository.flush();
        return toDomain(saved);
    }

    private List<SubscriptionTransformerStationEntity> buildAssociations(SubscriptionEntity subscription,
                                                                         Set<TransformerStation> stations) {
        return stations.stream()
                .map(station -> {
                    SubscriptionTransformerStationEntity association = new SubscriptionTransformerStationEntity();
                    association.setId(UUID.randomUUID());
                    association.setSubscription(subscription);
                    association.setTransformerStation(transformerStationMapper.toEntity(station));
                    return association;
                })
                .collect(Collectors.toList());
    }

    private Subscription toDomain(SubscriptionEntity entity) {
        Set<TransformerStation> stations = stationAssociationRepository.findAllBySubscriptionId(entity.getId())
                .stream()
                .map(association -> transformerStationMapper.toDomain(association.getTransformerStation()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return mapper.toDomain(entity, stations);
    }
}