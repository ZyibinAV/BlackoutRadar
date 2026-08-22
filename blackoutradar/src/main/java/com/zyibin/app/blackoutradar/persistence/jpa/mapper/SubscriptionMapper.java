package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {UserMapper.class, AddressMapper.class})
public interface SubscriptionMapper {

    SubscriptionEntity toEntity(Subscription subscription);

    Subscription toDomain(SubscriptionEntity entity, Set<TransformerStation> stations);

    User toUser(UserEntity entity);

    Address toAddress(AddressEntity entity);

    @ObjectFactory
    default Subscription createSubscription(SubscriptionEntity entity, Set<TransformerStation> stations) {
        return Subscription.of(entity.getId(), toUser(entity.getUser()), toAddress(entity.getAddress()),
                entity.getMonitoringStart(), entity.getMonitoringEnd(), entity.isActive(),
                entity.getServiceAccessUntil(), stations);
    }
}