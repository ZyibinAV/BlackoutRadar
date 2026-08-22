package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegionMapper {

    RegionEntity toEntity(Region region);

    Region toDomain(RegionEntity entity);

    @ObjectFactory
    default Region createRegion(RegionEntity entity) {
        return Region.of(entity.getId(), entity.getName());
    }
}