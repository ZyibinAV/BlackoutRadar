package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "powerOutage", ignore = true)
    NotificationEntity toEntity(Notification notification);

    Notification toDomain(NotificationEntity entity, Subscription subscription, PowerOutage powerOutage);

    @ObjectFactory
    default Notification createNotification(NotificationEntity entity, Subscription subscription,
                                          PowerOutage powerOutage) {
        return Notification.of(entity.getId(), subscription, powerOutage, entity.getMessage(),
                entity.getStatus());
    }
}
