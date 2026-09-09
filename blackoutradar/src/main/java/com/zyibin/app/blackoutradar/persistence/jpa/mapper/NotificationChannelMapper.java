package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationChannelEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {UserMapper.class})
public interface NotificationChannelMapper {

    NotificationChannelEntity toEntity(NotificationChannel channel);

    NotificationChannel toDomain(NotificationChannelEntity entity);

    User toUser(UserEntity entity);

    @ObjectFactory
    default NotificationChannel createNotificationChannel(NotificationChannelEntity entity) {
        return NotificationChannel.of(entity.getId(), toUser(entity.getUser()),
                entity.getType(), entity.getDestination(), entity.isEnabled());
    }
}
