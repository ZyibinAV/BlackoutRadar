package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import com.zyibin.app.blackoutradar.application.notification.RetryProcessingService;
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
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final NotificationDeliveryPort deliveryPort;
    private final RetryProcessingService processingService;
    private final TaskScheduler taskScheduler;
    private final Duration pollInterval;

    public RetryScheduler(NotificationDeliveryPort deliveryPort,
                          RetryProcessingService processingService,
                          TaskScheduler taskScheduler,
                          @Value("${blackoutradar.notification.retry.poll-interval:30s}") Duration pollInterval) {
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.processingService = Objects.requireNonNull(processingService, "processingService must not be null");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler must not be null");
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        this.pollInterval = pollInterval;
    }

    @PostConstruct
    public void schedule() {
        taskScheduler.scheduleWithFixedDelay(this::processDueDeliveries, pollInterval);
    }

    void processDueDeliveries() {
        Instant now = Instant.now();
        List<NotificationDelivery> due;
        try {
            due = deliveryPort.findReadyForProcessing(now, BATCH_SIZE);
        } catch (RuntimeException e) {
            log.error("Failed to load due notification deliveries", e);
            return;
        }
        for (NotificationDelivery delivery : due) {
            try {
                processingService.process(delivery.id(), now);
            } catch (RuntimeException e) {
                log.warn("Failed to process notification delivery {}", delivery.id(), e);
            }
        }
    }
}
