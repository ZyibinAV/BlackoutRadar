package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecoveryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final NotificationDeliveryPort deliveryPort;
    private final NotificationDeliveryFencingPort fencingPort;
    private final TaskScheduler taskScheduler;
    private final Duration pollInterval;
    private final Duration stuckThreshold;

    public RecoveryScheduler(NotificationDeliveryPort deliveryPort,
                             NotificationDeliveryFencingPort fencingPort,
                             TaskScheduler taskScheduler,
                             @Value("${blackoutradar.notification.recovery.poll-interval:5m}") Duration pollInterval,
                             @Value("${blackoutradar.notification.recovery.stuck-threshold:15m}") Duration stuckThreshold) {
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.fencingPort = Objects.requireNonNull(fencingPort, "fencingPort must not be null");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler must not be null");
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        Objects.requireNonNull(stuckThreshold, "stuckThreshold must not be null");
        if (stuckThreshold.isNegative() || stuckThreshold.isZero()) {
            throw new IllegalArgumentException("stuckThreshold must be positive");
        }
        this.pollInterval = pollInterval;
        this.stuckThreshold = stuckThreshold;
    }

    @PostConstruct
    public void schedule() {
        taskScheduler.scheduleWithFixedDelay(this::recoverStuckDeliveries, pollInterval);
    }

    void recoverStuckDeliveries() {
        Instant now = Instant.now();
        Instant stuckBefore = now.minus(stuckThreshold);
        List<NotificationDelivery> stuck;
        try {
            stuck = deliveryPort.findStuckDeliveries(stuckBefore, BATCH_SIZE);
        } catch (RuntimeException e) {
            log.error("Failed to load stuck notification deliveries: {}",
                    e.getClass().getSimpleName());
            return;
        }
        for (NotificationDelivery delivery : stuck) {
            try {
                fencingPort.recoverStuck(delivery.id(), stuckBefore);
            } catch (RuntimeException e) {
                log.warn("Failed to recover notification delivery {}: {}",
                        delivery.id(), e.getClass().getSimpleName());
            }
        }
    }
}
