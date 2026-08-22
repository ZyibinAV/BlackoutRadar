package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionalDistrictEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {RegionMapper.class, RegionalDistrictMapper.class})
public interface CityMapper {

    CityEntity toEntity(City city);

    City toDomain(CityEntity entity);

    Region toRegion(RegionEntity entity);

    RegionalDistrict toRegionalDistrict(RegionalDistrictEntity entity);

    @ObjectFactory
    default City createCity(CityEntity entity) {
        Region region = toRegion(entity.getRegion());
        if (entity.getRegionalDistrict() == null) {
            return City.of(entity.getId(), region, entity.getName());
        }
        RegionalDistrict regionalDistrict = toRegionalDistrict(entity.getRegionalDistrict());
        return City.of(entity.getId(), region, regionalDistrict, entity.getName());
    }
}