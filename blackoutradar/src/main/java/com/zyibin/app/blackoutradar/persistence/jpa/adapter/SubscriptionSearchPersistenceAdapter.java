package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionSearchPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.SubscriptionMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.TransformerStationMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionTransformerStationJpaRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionSearchPersistenceAdapter implements SubscriptionSearchPort {

    private final SubscriptionJpaRepository repository;
    private final SubscriptionTransformerStationJpaRepository stationAssociationRepository;
    private final SubscriptionMapper mapper;
    private final TransformerStationMapper transformerStationMapper;

    public SubscriptionSearchPersistenceAdapter(SubscriptionJpaRepository repository,
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
    public List<Subscription> findActiveByAddressIds(Set<UUID> addressIds) {
        if (addressIds == null || addressIds.isEmpty()) {
            return List.of();
        }
        List<SubscriptionEntity> entities = repository.findActiveByAddressIds(addressIds);
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    private Subscription toDomain(SubscriptionEntity entity) {
        Set<TransformerStation> stations = stationAssociationRepository.findAllBySubscriptionId(entity.getId())
                .stream()
                .map(association -> transformerStationMapper.toDomain(association.getTransformerStation()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return mapper.toDomain(entity, stations);
    }
}
