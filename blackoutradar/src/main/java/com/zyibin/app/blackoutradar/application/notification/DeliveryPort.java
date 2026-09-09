package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;

public interface DeliveryPort {

    String channelType();

    DeliveryResult deliver(NotificationChannel channel, String message);
}
