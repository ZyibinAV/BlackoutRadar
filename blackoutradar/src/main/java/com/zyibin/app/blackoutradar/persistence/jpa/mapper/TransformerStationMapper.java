package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransformerStationMapper {

    TransformerStationEntity toEntity(TransformerStation station);

    TransformerStation toDomain(TransformerStationEntity entity);

    @ObjectFactory
    default TransformerStation createTransformerStation(TransformerStationEntity entity) {
        return TransformerStation.of(entity.getId(), entity.getName());
    }
}