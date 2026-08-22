package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionalDistrictEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {RegionMapper.class})
public interface RegionalDistrictMapper {

    RegionalDistrictEntity toEntity(RegionalDistrict regionalDistrict);

    RegionalDistrict toDomain(RegionalDistrictEntity entity);

    Region toRegion(RegionEntity entity);

    @ObjectFactory
    default RegionalDistrict createRegionalDistrict(RegionalDistrictEntity entity) {
        return RegionalDistrict.of(entity.getId(), toRegion(entity.getRegion()), entity.getType(), entity.getName());
    }
}