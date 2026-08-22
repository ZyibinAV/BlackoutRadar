package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import java.util.Collection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {SourceMapper.class, AddressMapper.class, TransformerStationMapper.class})
public interface PowerOutageMapper {

    PowerOutageEntity toEntity(PowerOutage powerOutage);

    PowerOutage toDomain(PowerOutageEntity entity, Collection<PowerOutageAddress> powerOutageAddresses);

    @Mapping(target = "powerOutage", ignore = true)
    PowerOutageAddressEntity toAddressEntity(PowerOutageAddress address);

    PowerOutageAddress toDomain(PowerOutageAddressEntity entity);

    Source toSource(SourceEntity entity);

    Address toAddress(AddressEntity entity);

    TransformerStation toTransformerStation(TransformerStationEntity entity);

    @ObjectFactory
    default PowerOutage createPowerOutage(PowerOutageEntity entity,
                                           Collection<PowerOutageAddress> powerOutageAddresses) {
        return PowerOutage.of(entity.getId(), toSource(entity.getSource()), entity.getStartTime(),
                entity.getEndTime(), entity.getReason(), entity.getStatus(), powerOutageAddresses);
    }

    @ObjectFactory
    default PowerOutageAddress createPowerOutageAddress(PowerOutageAddressEntity entity) {
        return PowerOutageAddress.unboundOf(entity.getId(), toAddress(entity.getAddress()),
                toTransformerStation(entity.getTransformerStation()));
    }
}