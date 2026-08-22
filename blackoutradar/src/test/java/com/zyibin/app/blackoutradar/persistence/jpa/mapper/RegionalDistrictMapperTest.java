package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionalDistrictEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class RegionalDistrictMapperTest {

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private RegionalDistrictMapper mapper;

    @Test
    void mapsDomainToEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");

        RegionalDistrictEntity entity = mapper.toEntity(district);

        assertEquals(district.id(), entity.getId());
        assertEquals(district.type(), entity.getType());
        assertEquals(district.name(), entity.getName());
        assertEquals(region.id(), entity.getRegion().getId());
    }

    @Test
    void mapsEntityToDomain() {
        RegionEntity regionEntity = regionMapper.toEntity(Region.of(UUID.randomUUID(), "Омская область"));
        RegionalDistrictEntity entity = new RegionalDistrictEntity();
        entity.setId(UUID.randomUUID());
        entity.setType(RegionalDistrictType.MUNICIPAL_OKRUG);
        entity.setName("Центральный округ");
        entity.setRegion(regionEntity);

        RegionalDistrict district = mapper.toDomain(entity);

        assertEquals(entity.getId(), district.id());
        assertEquals(RegionalDistrictType.MUNICIPAL_OKRUG, district.type());
        assertEquals("Центральный округ", district.name());
        assertEquals(regionEntity.getId(), district.region().id());
    }

    @Test
    void roundTripPreservesIdentity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict original = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");

        RegionalDistrict restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.region().id(), restored.region().id());
        assertEquals(original.type(), restored.type());
        assertEquals(original.name(), restored.name());
    }
}