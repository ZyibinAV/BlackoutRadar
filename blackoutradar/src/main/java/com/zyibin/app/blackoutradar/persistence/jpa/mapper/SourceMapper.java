package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SourceMapper {

    SourceEntity toEntity(Source source);

    Source toDomain(SourceEntity entity);

    @ObjectFactory
    default Source createSource(SourceEntity entity) {
        if (entity.getConfiguration() == null) {
            return Source.of(entity.getId(), entity.getName(), entity.getSourceType(),
                    entity.getProviderType(), entity.getSchedule(), entity.isActive());
        }
        return Source.of(entity.getId(), entity.getName(), entity.getSourceType(),
                entity.getProviderType(), entity.getConfiguration(), entity.getSchedule(), entity.isActive());
    }
}