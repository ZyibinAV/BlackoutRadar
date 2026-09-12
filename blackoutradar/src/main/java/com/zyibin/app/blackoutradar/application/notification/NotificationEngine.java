package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationEngine {

    private static final Logger log = LoggerFactory.getLogger(NotificationEngine.class);

    private final NotificationPort notificationPort;
    private final NotificationChannelPort channelPort;
    private final DeliveryChannelRegistry channelRegistry;

    public NotificationEngine(NotificationPort notificationPort,
                              NotificationChannelPort channelPort,
                              DeliveryChannelRegistry channelRegistry) {
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort must not be null");
        this.channelPort = Objects.requireNonNull(channelPort, "channelPort must not be null");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry must not be null");
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
        boolean allDelivered = !channels.isEmpty();
        for (NotificationChannel channel : channels) {
            if (!deliverToChannel(processing, channel)) {
                allDelivered = false;
            }
        }
        Notification result = allDelivered ? processing.markSent() : processing.markFailed();
        return notificationPort.save(result);
    }

    private boolean deliverToChannel(Notification notification, NotificationChannel channel) {
        var adapter = channelRegistry.find(channel.type());
        if (adapter.isEmpty()) {
            log.warn("No delivery adapter registered for channel type {}", channel.type());
            return false;
        }
        try {
            return adapter.get().deliver(channel, notification.message()).successful();
        } catch (RuntimeException e) {
            log.warn("Delivery failed for channel {} to {}", channel.type(), channel.destination(), e);
            return false;
        }
    }
}
