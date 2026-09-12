package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationDeliveryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationDeliveryMapper {

    @Mapping(target = "notification", ignore = true)
    @Mapping(target = "notificationChannel", ignore = true)
    NotificationDeliveryEntity toEntity(NotificationDelivery delivery);

    NotificationDelivery toDomain(NotificationDeliveryEntity entity, Notification notification,
                                  NotificationChannel notificationChannel);

    @ObjectFactory
    default NotificationDelivery createNotificationDelivery(NotificationDeliveryEntity entity,
                                                            Notification notification,
                                                            NotificationChannel notificationChannel) {
        return NotificationDelivery.of(entity.getId(), notification, notificationChannel,
                entity.getStatus(), entity.getNextAttemptAt());
    }
}
