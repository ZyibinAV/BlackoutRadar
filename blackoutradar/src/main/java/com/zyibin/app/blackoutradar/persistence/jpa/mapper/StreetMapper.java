package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {CityMapper.class})
public interface StreetMapper {

    StreetEntity toEntity(Street street);

    Street toDomain(StreetEntity entity);

    City toCity(CityEntity entity);

    @ObjectFactory
    default Street createStreet(StreetEntity entity) {
        return Street.of(entity.getId(), toCity(entity.getCity()), entity.getType(), entity.getCanonicalName());
    }
}