package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttempt;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.DeliveryAttemptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeliveryAttemptMapper {

    @Mapping(target = "notificationDelivery", ignore = true)
    DeliveryAttemptEntity toEntity(DeliveryAttempt attempt);

    DeliveryAttempt toDomain(DeliveryAttemptEntity entity, NotificationDelivery notificationDelivery);

    @ObjectFactory
    default DeliveryAttempt createDeliveryAttempt(DeliveryAttemptEntity entity,
                                                  NotificationDelivery notificationDelivery) {
        if (entity.getCompletedAt() == null) {
            return DeliveryAttempt.started(entity.getId(), notificationDelivery,
                    entity.getAttemptNumber(), entity.getStartedAt());
        }
        return DeliveryAttempt.completed(entity.getId(), notificationDelivery,
                entity.getAttemptNumber(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getResult(), entity.getErrorCode());
    }
}
