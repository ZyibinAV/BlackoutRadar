package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class StreetMapperTest {

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private RegionalDistrictMapper regionalDistrictMapper;

    @Autowired
    private CityMapper cityMapper;

    @Autowired
    private StreetMapper mapper;

    @Test
    void mapsDomainToEntity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");

        StreetEntity entity = mapper.toEntity(street);

        assertEquals(street.id(), entity.getId());
        assertEquals(street.type(), entity.getType());
        assertEquals(street.canonicalName(), entity.getCanonicalName());
        assertEquals(city.id(), entity.getCity().getId());
    }

    @Test
    void mapsEntityToDomain() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        StreetEntity entity = new StreetEntity();
        entity.setId(UUID.randomUUID());
        entity.setType(StreetType.PROSPECT);
        entity.setCanonicalName("Мира");
        entity.setCity(cityMapper.toEntity(city));

        Street street = mapper.toDomain(entity);

        assertEquals(entity.getId(), street.id());
        assertEquals(StreetType.PROSPECT, street.type());
        assertEquals("Мира", street.canonicalName());
        assertEquals(city.id(), street.city().id());
    }

    @Test
    void roundTripPreservesIdentity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street original = Street.of(UUID.randomUUID(), city, StreetType.BOULEVARD, "Ленина");

        Street restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.city().id(), restored.city().id());
        assertEquals(original.type(), restored.type());
        assertEquals(original.canonicalName(), restored.canonicalName());
    }
}