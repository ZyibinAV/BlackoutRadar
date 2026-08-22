package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class CityMapperTest {

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private RegionalDistrictMapper regionalDistrictMapper;

    @Autowired
    private CityMapper mapper;

    @Test
    void mapsCityWithoutRegionalDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");

        CityEntity entity = mapper.toEntity(city);

        assertEquals(city.id(), entity.getId());
        assertEquals(city.name(), entity.getName());
        assertEquals(region.id(), entity.getRegion().getId());
        assertNull(entity.getRegionalDistrict());
    }

    @Test
    void mapsCityWithRegionalDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");
        City city = City.of(UUID.randomUUID(), region, district, "Лузино");

        CityEntity entity = mapper.toEntity(city);

        assertEquals(city.id(), entity.getId());
        assertEquals(region.id(), entity.getRegion().getId());
        assertEquals(district.id(), entity.getRegionalDistrict().getId());
    }

    @Test
    void mapsEntityToCityWithoutRegionalDistrict() {
        RegionEntitySource source = buildEntityGraph();

        City city = mapper.toDomain(source.entity);

        assertEquals(source.entity.getId(), city.id());
        assertEquals(source.entity.getName(), city.name());
        assertEquals(source.region.id(), city.region().id());
        assertNull(city.regionalDistrict());
    }

    @Test
    void mapsEntityToCityWithRegionalDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.URBAN_OKRUG, "Округ");
        City city = City.of(UUID.randomUUID(), region, district, "Город");

        City restored = mapper.toDomain(mapper.toEntity(city));

        assertEquals(city.id(), restored.id());
        assertEquals(city.region().id(), restored.region().id());
        assertEquals(city.regionalDistrict().id(), restored.regionalDistrict().id());
        assertEquals(city.name(), restored.name());
    }

    @Test
    void mapsCityWithRegionalDistrictToDomainUsingAllMappers() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict district = regionalDistrictMapper.toDomain(
                regionalDistrictMapper.toEntity(RegionalDistrict.of(
                        UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район")));
        City city = City.of(UUID.randomUUID(), region, district, "Лузино");

        City restored = mapper.toDomain(mapper.toEntity(city));

        assertEquals(city.id(), restored.id());
        assertEquals(region.id(), restored.region().id());
        assertEquals(district.id(), restored.regionalDistrict().id());
        assertEquals("Лузино", restored.name());
    }

    private RegionEntitySource buildEntityGraph() {
        RegionEntitySource source = new RegionEntitySource();
        source.region = Region.of(UUID.randomUUID(), "Омская область");
        source.entity = new CityEntity();
        source.entity.setId(UUID.randomUUID());
        source.entity.setName("Омск");
        source.entity.setRegion(regionMapper.toEntity(source.region));
        return source;
    }

    private static final class RegionEntitySource {
        private Region region;
        private CityEntity entity;
    }
}