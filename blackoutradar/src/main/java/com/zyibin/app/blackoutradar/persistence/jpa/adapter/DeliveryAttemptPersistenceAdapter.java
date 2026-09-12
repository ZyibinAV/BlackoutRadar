package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.DeliveryAttemptPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.DeliveryAttemptEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.DeliveryAttemptMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.DeliveryAttemptJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationDeliveryJpaRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryAttemptPersistenceAdapter implements DeliveryAttemptPort {

    private final DeliveryAttemptJpaRepository repository;
    private final DeliveryAttemptMapper mapper;
    private final NotificationDeliveryJpaRepository deliveryRepository;
    private final NotificationDeliveryPort deliveryPort;

    public DeliveryAttemptPersistenceAdapter(DeliveryAttemptJpaRepository repository,
                                             DeliveryAttemptMapper mapper,
                                             NotificationDeliveryJpaRepository deliveryRepository,
                                             NotificationDeliveryPort deliveryPort) {
        this.repository = repository;
        this.mapper = mapper;
        this.deliveryRepository = deliveryRepository;
        this.deliveryPort = deliveryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAttempt> findByNotificationDeliveryId(UUID notificationDeliveryId) {
        return repository.findByNotificationDeliveryIdOrderByAttemptNumberAsc(notificationDeliveryId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public DeliveryAttempt save(DeliveryAttempt attempt) {
        DeliveryAttemptEntity entity = mapper.toEntity(attempt);
        entity.setNotificationDelivery(
                deliveryRepository.getReferenceById(attempt.notificationDelivery().id()));
        DeliveryAttemptEntity saved = repository.save(entity);
        repository.flush();
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public int nextAttemptNumber(UUID notificationDeliveryId) {
        return repository.maxAttemptNumber(notificationDeliveryId) + 1;
    }

    private DeliveryAttempt toDomain(DeliveryAttemptEntity entity) {
        NotificationDelivery delivery = deliveryPort.findById(entity.getNotificationDelivery().getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "NotificationDelivery not found: " + entity.getNotificationDelivery().getId()));
        return mapper.toDomain(entity, delivery);
    }
}
