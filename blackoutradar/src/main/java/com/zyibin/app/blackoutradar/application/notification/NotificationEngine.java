package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationEngine {

    private final NotificationPort notificationPort;
    private final NotificationChannelPort channelPort;
    private final NotificationDeliveryPort deliveryPort;
    private final RetryProcessingService retryProcessingService;
    private final NotificationFinalizationService finalizationService;
    private final Clock clock;

    @Autowired
    public NotificationEngine(NotificationPort notificationPort,
                              NotificationChannelPort channelPort,
                              NotificationDeliveryPort deliveryPort,
                              RetryProcessingService retryProcessingService,
                              NotificationFinalizationService finalizationService) {
        this(notificationPort, channelPort, deliveryPort, retryProcessingService,
                finalizationService, Clock.systemUTC());
    }

    public NotificationEngine(NotificationPort notificationPort,
                              NotificationChannelPort channelPort,
                              NotificationDeliveryPort deliveryPort,
                              RetryProcessingService retryProcessingService,
                              NotificationFinalizationService finalizationService,
                              Clock clock) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort must not be null");
        this.channelPort = Objects.requireNonNull(channelPort, "channelPort must not be null");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.retryProcessingService =
                Objects.requireNonNull(retryProcessingService, "retryProcessingService must not be null");
        this.finalizationService =
                Objects.requireNonNull(finalizationService, "finalizationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Notification process(UUID notificationId) {
        Objects.requireNonNull(notificationId, "notificationId must not be null");
        Optional<Notification> claimed = notificationPort.claimForProcessing(notificationId);
        if (claimed.isEmpty()) {
            return notificationPort.findById(notificationId)
                    .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        }
        Notification processing = claimed.get();
        List<NotificationChannel> channels = channelPort.findByUserId(processing.subscription().user().id())
                .stream()
                .filter(NotificationChannel::isEnabled)
                .toList();
        Instant now = Instant.now(clock);
        List<NotificationDelivery> deliveries = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            deliveries.add(deliveryPort.save(
                    NotificationDelivery.of(UUID.randomUUID(), processing, channel)));
        }
        if (deliveries.isEmpty()) {
            return finalizationService.finalizeNotification(notificationId);
        }
        for (NotificationDelivery delivery : deliveries) {
            retryProcessingService.process(delivery.id(), now);
        }
        return notificationPort.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
    }
}
