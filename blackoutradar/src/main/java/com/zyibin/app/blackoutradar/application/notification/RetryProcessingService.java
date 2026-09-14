package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.DeliveryAttemptPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RetryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RetryProcessingService.class);

    private final NotificationDeliveryPort deliveryPort;
    private final NotificationDeliveryFencingPort fencingPort;
    private final DeliveryAttemptPort attemptPort;
    private final DeliveryChannelRegistry channelRegistry;
    private final RetryPolicy retryPolicy;
    private final NotificationFinalizationService finalizationService;
    private final Clock clock;

    @Autowired
    public RetryProcessingService(NotificationDeliveryPort deliveryPort,
                                  NotificationDeliveryFencingPort fencingPort,
                                  DeliveryAttemptPort attemptPort,
                                  DeliveryChannelRegistry channelRegistry,
                                  RetryPolicy retryPolicy,
                                  NotificationFinalizationService finalizationService) {
        this(deliveryPort, fencingPort, attemptPort, channelRegistry, retryPolicy,
                finalizationService, Clock.systemUTC());
    }

    public RetryProcessingService(NotificationDeliveryPort deliveryPort,
                                  NotificationDeliveryFencingPort fencingPort,
                                  DeliveryAttemptPort attemptPort,
                                  DeliveryChannelRegistry channelRegistry,
                                  RetryPolicy retryPolicy,
                                  NotificationFinalizationService finalizationService,
                                  Clock clock) {
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.fencingPort = Objects.requireNonNull(fencingPort, "fencingPort must not be null");
        this.attemptPort = Objects.requireNonNull(attemptPort, "attemptPort must not be null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.finalizationService =
                Objects.requireNonNull(finalizationService, "finalizationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public DeliveryProcessingOutcome process(UUID deliveryId, Instant now) {
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Optional<DeliveryClaim> claimed = fencingPort.claim(deliveryId, now);
        if (claimed.isEmpty()) {
            return new DeliveryProcessingOutcome(deliveryPort.findById(deliveryId)
                    .orElseThrow(() -> new NoSuchElementException("NotificationDelivery not found: " + deliveryId)),
                    false);
        }
        DeliveryClaim claim = claimed.get();
        NotificationDelivery processing = claim.delivery();
        UUID ownershipToken = claim.ownershipToken();
        int attemptNumber = attemptPort.nextAttemptNumber(processing.id());
        DeliveryAttempt started = attemptPort.save(
                DeliveryAttempt.started(UUID.randomUUID(), processing, attemptNumber, now));
        DeliveryAttemptResult attemptResult = executeDelivery(processing);
        DeliveryAttempt completed = attemptPort.save(
                started.complete(Instant.now(clock), attemptResult, null));
        NotificationDelivery result = applyResult(processing, completed, now);
        Optional<NotificationDelivery> saved = fencingPort.saveIfOwned(processing.id(), ownershipToken, result);
        if (saved.isPresent()) {
            finalizationService.finalizeNotification(processing.notification().id());
            return new DeliveryProcessingOutcome(saved.get(), true);
        }
        log.warn("Lost ownership for notification delivery {}", deliveryId);
        return new DeliveryProcessingOutcome(deliveryPort.findById(deliveryId)
                .orElseThrow(() -> new NoSuchElementException("NotificationDelivery not found: " + deliveryId)),
                false);
    }

    private DeliveryAttemptResult executeDelivery(NotificationDelivery processing) {
        NotificationChannel channel = processing.notificationChannel();
        Optional<DeliveryPort> adapter = channelRegistry.find(channel.type());
        if (adapter.isEmpty()) {
            log.warn("No delivery adapter registered for channel type {}", channel.type());
            return DeliveryAttemptResult.PERMANENT_FAILURE;
        }
        try {
            return switch (adapter.get().deliver(channel, processing.notification().message()).outcome()) {
                case SUCCESS -> DeliveryAttemptResult.SUCCESS;
                case TEMPORARY_FAILURE -> DeliveryAttemptResult.TEMPORARY_FAILURE;
                case PERMANENT_FAILURE -> DeliveryAttemptResult.PERMANENT_FAILURE;
            };
        } catch (RuntimeException e) {
            log.warn("Delivery failed for channel type {}: {}", channel.type(),
                    e.getClass().getSimpleName());
            return DeliveryAttemptResult.TEMPORARY_FAILURE;
        }
    }

    private NotificationDelivery applyResult(NotificationDelivery processing, DeliveryAttempt completed,
                                             Instant now) {
        if (completed.result() == DeliveryAttemptResult.SUCCESS) {
            return processing.markSent();
        }
        if (completed.result() == DeliveryAttemptResult.PERMANENT_FAILURE) {
            return processing.markFailed();
        }
        RetryDecision decision = retryPolicy.decide(completed.result(), completed.attemptNumber(), now);
        if (decision.retryAllowed()) {
            return processing.scheduleRetry(decision.nextAttemptAt());
        }
        return processing.markFailed();
    }
}
