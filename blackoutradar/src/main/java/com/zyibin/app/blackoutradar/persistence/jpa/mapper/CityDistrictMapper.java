package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {CityMapper.class})
public interface CityDistrictMapper {

    CityDistrictEntity toEntity(CityDistrict cityDistrict);

    CityDistrict toDomain(CityDistrictEntity entity);

    City toCity(CityEntity entity);

    @ObjectFactory
    default CityDistrict createCityDistrict(CityDistrictEntity entity) {
        return CityDistrict.of(entity.getId(), toCity(entity.getCity()), entity.getName());
    }
}