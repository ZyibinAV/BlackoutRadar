package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class CityDistrictMapperTest {

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private RegionalDistrictMapper regionalDistrictMapper;

    @Autowired
    private CityMapper cityMapper;

    @Autowired
    private CityDistrictMapper mapper;

    @Test
    void mapsDomainToEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");

        CityDistrictEntity entity = mapper.toEntity(district);

        assertEquals(district.id(), entity.getId());
        assertEquals(district.name(), entity.getName());
        assertEquals(city.id(), entity.getCity().getId());
    }

    @Test
    void mapsEntityToDomain() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrictEntity entity = new CityDistrictEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Центральный");
        entity.setCity(cityMapper.toEntity(city));

        CityDistrict district = mapper.toDomain(entity);

        assertEquals(entity.getId(), district.id());
        assertEquals("Центральный", district.name());
        assertEquals(city.id(), district.city().id());
    }

    @Test
    void roundTripPreservesIdentity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict original = CityDistrict.of(UUID.randomUUID(), city, "Кировский");

        CityDistrict restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.city().id(), restored.city().id());
        assertEquals(original.name(), restored.name());
    }
}