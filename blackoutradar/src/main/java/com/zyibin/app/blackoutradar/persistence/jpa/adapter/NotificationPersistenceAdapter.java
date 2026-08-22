package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.NotificationMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.PowerOutageMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.SubscriptionMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.TransformerStationMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageAddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageJpaRepository;
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
public class NotificationPersistenceAdapter implements NotificationPort {

    private final NotificationJpaRepository repository;
    private final NotificationMapper mapper;
    private final SubscriptionJpaRepository subscriptionRepository;
    private final SubscriptionTransformerStationJpaRepository stationAssociationRepository;
    private final TransformerStationMapper transformerStationMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final PowerOutageJpaRepository powerOutageRepository;
    private final PowerOutageAddressJpaRepository addressRepository;
    private final PowerOutageMapper powerOutageMapper;

    public NotificationPersistenceAdapter(NotificationJpaRepository repository,
                                        NotificationMapper mapper,
                                        SubscriptionJpaRepository subscriptionRepository,
                                        SubscriptionTransformerStationJpaRepository stationAssociationRepository,
                                        TransformerStationMapper transformerStationMapper,
                                        SubscriptionMapper subscriptionMapper,
                                        PowerOutageJpaRepository powerOutageRepository,
                                        PowerOutageAddressJpaRepository addressRepository,
                                        PowerOutageMapper powerOutageMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.subscriptionRepository = subscriptionRepository;
        this.stationAssociationRepository = stationAssociationRepository;
        this.transformerStationMapper = transformerStationMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.powerOutageRepository = powerOutageRepository;
        this.addressRepository = addressRepository;
        this.powerOutageMapper = powerOutageMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findBySubscriptionAndPowerOutage(UUID subscriptionId,
                                                                   UUID powerOutageId) {
        return repository.findBySubscriptionIdAndPowerOutageId(subscriptionId, powerOutageId)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Notification save(Notification notification) {
        NotificationEntity entity = mapper.toEntity(notification);
        entity.setSubscription(subscriptionRepository.getReferenceById(notification.subscription().id()));
        entity.setPowerOutage(powerOutageRepository.getReferenceById(notification.powerOutage().id()));
        NotificationEntity saved = repository.save(entity);
        repository.flush();
        return toDomain(saved);
    }

    private Notification toDomain(NotificationEntity entity) {
        SubscriptionEntity subscriptionEntity = entity.getSubscription();
        PowerOutageEntity powerOutageEntity = entity.getPowerOutage();
        // Ensure entities are initialized; if they are proxies from getReference, they will be hit on id access
        // Hydrate full domain graphs to avoid recursive mapping and to preserve existing conventions
        Set<TransformerStation> stations = stationAssociationRepository
                .findAllBySubscriptionId(subscriptionEntity.getId())
                .stream()
                .map(assoc -> transformerStationMapper.toDomain(assoc.getTransformerStation()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        com.zyibin.app.blackoutradar.domain.subscription.Subscription subscription =
                subscriptionMapper.toDomain(subscriptionEntity, stations);

        List<PowerOutageAddress> addresses = addressRepository
                .findAllByPowerOutageId(powerOutageEntity.getId())
                .stream()
                .map(powerOutageMapper::toDomain)
                .collect(Collectors.toList());
        com.zyibin.app.blackoutradar.domain.outage.PowerOutage powerOutage =
                powerOutageMapper.toDomain(powerOutageEntity, addresses);

        return mapper.toDomain(entity, subscription, powerOutage);
    }
}
