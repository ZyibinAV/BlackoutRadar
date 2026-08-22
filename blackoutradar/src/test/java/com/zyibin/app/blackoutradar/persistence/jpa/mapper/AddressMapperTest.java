package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class AddressMapperTest {

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private RegionalDistrictMapper regionalDistrictMapper;

    @Autowired
    private CityMapper cityMapper;

    @Autowired
    private CityDistrictMapper cityDistrictMapper;

    @Autowired
    private StreetMapper streetMapper;

    @Autowired
    private AddressMapper mapper;

    @Test
    void mapsDomainToEntityWithoutCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));

        AddressEntity entity = mapper.toEntity(address);

        assertEquals(address.id(), entity.getId());
        assertEquals(street.id(), entity.getStreet().getId());
        assertNull(entity.getCityDistrict());
        assertEquals("15", entity.getHouseNumber());
        assertNull(entity.getHouseAddition());
        assertEquals("15", entity.getCanonicalHouse());
        assertEquals(city.id(), entity.getCity().getId());
    }

    @Test
    void mapsDomainToEntityWithCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1"));

        AddressEntity entity = mapper.toEntity(address);

        assertEquals(street.id(), entity.getStreet().getId());
        assertEquals(district.id(), entity.getCityDistrict().getId());
        assertEquals(city.id(), entity.getCity().getId());
        assertEquals("15", entity.getHouseNumber());
        assertEquals("к1", entity.getHouseAddition());
        assertEquals("15к1", entity.getCanonicalHouse());
    }

    @Test
    void persistenceCityIdDerivedFromStreetCity() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));

        AddressEntity entity = mapper.toEntity(address);

        assertEquals(street.city().id(), entity.getCity().getId());
        assertEquals(entity.getStreet().getCity().getId(), entity.getCity().getId());
    }

    @Test
    void mapsEntityToDomainWithoutCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        AddressEntity entity = new AddressEntity();
        entity.setId(UUID.randomUUID());
        entity.setStreet(streetMapper.toEntity(street));
        entity.setCity(cityMapper.toEntity(city));
        entity.setHouseNumber("15");
        entity.setHouseAddition(null);
        entity.setCanonicalHouse("15");

        Address address = mapper.toDomain(entity);

        assertEquals(entity.getId(), address.id());
        assertEquals(street.id(), address.street().id());
        assertEquals(city.id(), address.street().city().id());
        assertNull(address.cityDistrict());
        assertEquals(new House("15", null, "15"), address.house());
    }

    @Test
    void mapsEntityToDomainWithCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        AddressEntity entity = new AddressEntity();
        entity.setId(UUID.randomUUID());
        entity.setStreet(streetMapper.toEntity(street));
        entity.setCity(cityMapper.toEntity(city));
        entity.setCityDistrict(cityDistrictMapper.toEntity(district));
        entity.setHouseNumber("15");
        entity.setHouseAddition("к1");
        entity.setCanonicalHouse("15к1");

        Address address = mapper.toDomain(entity);

        assertEquals(entity.getId(), address.id());
        assertEquals(district.id(), address.cityDistrict().id());
        assertEquals(new House("15", "к1", "15к1"), address.house());
        assertEquals(city.id(), address.street().city().id());
    }

    @Test
    void persistenceCityIdDoesNotLeakIntoDomain() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));

        Address restored = mapper.toDomain(mapper.toEntity(address));

        assertEquals(street.city().id(), restored.street().city().id());
        assertEquals(address.id(), restored.id());
        assertEquals(address.house(), restored.house());
    }

    @Test
    void roundTripPreservesIdentityWithCityDistrict() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address original = Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1"));

        Address restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.street().id(), restored.street().id());
        assertEquals(original.cityDistrict().id(), restored.cityDistrict().id());
        assertEquals(original.house(), restored.house());
    }

    @Test
    void nestedMappingPreservesStreetCityRegionGraph() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict regionalDistrict =
                RegionalDistrict.of(UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");
        City city = City.of(UUID.randomUUID(), region, regionalDistrict, "Лузино");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));

        AddressEntity entity = mapper.toEntity(address);
        Address restored = mapper.toDomain(entity);

        assertEquals(region.id(), restored.street().city().region().id());
        assertEquals(regionalDistrict.id(), restored.street().city().regionalDistrict().id());
        assertEquals(city.id(), restored.street().city().id());
        assertEquals(region.id(), entity.getStreet().getCity().getRegion().getId());
        assertEquals(regionalDistrict.id(), entity.getStreet().getCity().getRegionalDistrict().getId());
        assertEquals(city.id(), entity.getStreet().getCity().getId());
        assertEquals(city.id(), entity.getCity().getId());
    }

    @Test
    void nestedMappingPreservesCityDistrictCityRegionGraph() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        RegionalDistrict regionalDistrict =
                RegionalDistrict.of(UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_OKRUG, "Омский округ");
        City city = City.of(UUID.randomUUID(), region, regionalDistrict, "Лузино");
        CityDistrict district = CityDistrict.of(UUID.randomUUID(), city, "Центральный");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1"));

        Address restored = mapper.toDomain(mapper.toEntity(address));

        assertEquals(region.id(), restored.cityDistrict().city().region().id());
        assertEquals(regionalDistrict.id(), restored.cityDistrict().city().regionalDistrict().id());
        assertEquals(city.id(), restored.cityDistrict().city().id());
        assertEquals(restored.street().city().id(), restored.cityDistrict().city().id());
    }

    @Test
    void reverseMappingWithoutCityDistrictKeepsNull() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        AddressEntity entity = new AddressEntity();
        entity.setId(UUID.randomUUID());
        entity.setStreet(streetMapper.toEntity(street));
        entity.setCity(cityMapper.toEntity(city));
        entity.setHouseNumber("15");
        entity.setHouseAddition(null);
        entity.setCanonicalHouse("15");

        Address address = mapper.toDomain(entity);

        assertNull(address.cityDistrict());
    }
}