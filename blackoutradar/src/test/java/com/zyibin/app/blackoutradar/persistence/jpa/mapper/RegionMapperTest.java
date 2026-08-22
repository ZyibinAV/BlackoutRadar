package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class RegionMapperTest {

    @Autowired
    private RegionMapper mapper;

    @Test
    void mapsDomainToEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");

        RegionEntity entity = mapper.toEntity(region);

        assertEquals(region.id(), entity.getId());
        assertEquals(region.name(), entity.getName());
    }

    @Test
    void mapsEntityToDomain() {
        UUID id = UUID.randomUUID();
        RegionEntity entity = new RegionEntity();
        entity.setId(id);
        entity.setName("Омская область");

        Region region = mapper.toDomain(entity);

        assertEquals(id, region.id());
        assertEquals("Омская область", region.name());
    }

    @Test
    void roundTripPreservesIdentity() {
        Region original = Region.of(UUID.randomUUID(), "Омская область");

        Region restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.name(), restored.name());
    }
}