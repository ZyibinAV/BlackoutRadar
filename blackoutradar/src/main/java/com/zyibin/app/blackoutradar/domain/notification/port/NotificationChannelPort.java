package com.zyibin.app.blackoutradar.domain.notification.port;

import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationChannelPort {

    Optional<NotificationChannel> findById(UUID id);

    List<NotificationChannel> findByUserId(UUID userId);

    NotificationChannel save(NotificationChannel channel);
}
