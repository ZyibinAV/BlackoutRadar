package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.application.notification.DeliveryClaim;
import com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationDeliveryEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.NotificationDeliveryMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationChannelJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationDeliveryJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDeliveryPersistenceAdapter
        implements NotificationDeliveryPort, NotificationDeliveryFencingPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryPersistenceAdapter.class);

    private final NotificationDeliveryJpaRepository repository;
    private final NotificationDeliveryMapper mapper;
    private final NotificationJpaRepository notificationRepository;
    private final NotificationChannelJpaRepository channelRepository;
    private final NotificationPort notificationPort;
    private final NotificationChannelPort channelPort;

    public NotificationDeliveryPersistenceAdapter(NotificationDeliveryJpaRepository repository,
                                                  NotificationDeliveryMapper mapper,
                                                  NotificationJpaRepository notificationRepository,
                                                  NotificationChannelJpaRepository channelRepository,
                                                  NotificationPort notificationPort,
                                                  NotificationChannelPort channelPort) {
        this.repository = repository;
        this.mapper = mapper;
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.notificationPort = notificationPort;
        this.channelPort = channelPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationDelivery> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDelivery> findByNotificationId(UUID notificationId) {
        return repository.findByNotificationId(notificationId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDelivery> findReadyForProcessing(Instant now, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return repository.findDueForProcessing(now, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDelivery> findStuckDeliveries(Instant stuckBefore, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return repository.findStuckDeliveries(stuckBefore, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public NotificationDelivery save(NotificationDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery must not be null");
        UUID id = delivery.id();
        Optional<NotificationDeliveryEntity> existing = repository.findById(id);
        NotificationDeliveryEntity entity = mapper.toEntity(delivery);
        entity.setNotification(notificationRepository.getReferenceById(delivery.notification().id()));
        entity.setNotificationChannel(channelRepository.getReferenceById(delivery.notificationChannel().id()));
        if (existing.isPresent()) {
            if (delivery.status() == DeliveryStatus.PROCESSING) {
                entity.setProcessingToken(existing.get().getProcessingToken());
            } else {
                entity.setProcessingToken(null);
            }
        } else {
            entity.setProcessingToken(null);
        }
        NotificationDeliveryEntity saved = repository.save(entity);
        repository.flush();
        return toDomain(saved);
    }

    @Override
    @Transactional
    public Optional<NotificationDelivery> claimForProcessing(UUID id, Instant now) {
        return claim(id, now).map(DeliveryClaim::delivery);
    }

    @Override
    @Transactional
    public Optional<DeliveryClaim> claim(UUID deliveryId, Instant now) {
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        UUID token = UUID.randomUUID();
        int updated = repository.claimReadyAsProcessingWithToken(deliveryId, now, token);
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findById(deliveryId)
                .map(this::toDomain)
                .map(delivery -> new DeliveryClaim(delivery, token));
    }

    @Override
    @Transactional
    public Optional<NotificationDelivery> saveIfOwned(UUID deliveryId, UUID ownershipToken,
                                                      NotificationDelivery newState) {
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(ownershipToken, "ownershipToken must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
        if (!deliveryId.equals(newState.id())) {
            throw new IllegalArgumentException("delivery id mismatch");
        }
        if (newState.status() == DeliveryStatus.PROCESSING) {
            throw new IllegalArgumentException("saveIfOwned must not target PROCESSING");
        }
        int updated = repository.updateIfOwned(deliveryId, ownershipToken,
                newState.status(), newState.nextAttemptAt());
        if (updated == 0) {
            log.warn("Lost ownership for notification delivery {}", deliveryId);
            return Optional.empty();
        }
        return repository.findById(deliveryId).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean recoverStuck(UUID deliveryId, Instant stuckBefore) {
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(stuckBefore, "stuckBefore must not be null");
        int updated = repository.recoverStuck(deliveryId, stuckBefore);
        if (updated == 0) {
            return false;
        }
        log.warn("Recovered stuck notification delivery {}", deliveryId);
        return true;
    }

    private NotificationDelivery toDomain(NotificationDeliveryEntity entity) {
        Notification notification = notificationPort.findById(entity.getNotification().getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Notification not found: " + entity.getNotification().getId()));
        NotificationChannel channel = channelPort.findById(entity.getNotificationChannel().getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "NotificationChannel not found: " + entity.getNotificationChannel().getId()));
        return mapper.toDomain(entity, notification, channel);
    }
}
