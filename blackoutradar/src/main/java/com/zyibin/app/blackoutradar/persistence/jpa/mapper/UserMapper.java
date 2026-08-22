package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserEntity toEntity(User user);

    User toDomain(UserEntity entity);

    @ObjectFactory
    default User createUser(UserEntity entity) {
        return User.of(entity.getId(), entity.getEmail(), entity.getRole(), entity.isActive(),
                entity.getNickname(), entity.getAbout(), entity.getAvatar());
    }
}