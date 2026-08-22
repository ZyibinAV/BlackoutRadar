package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.AddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityDistrictJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.StreetJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AddressIntegrityTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private CityDistrictPort cityDistrictPort;

    @Autowired
    private StreetPort streetPort;

    @Autowired
    private AddressPort addressPort;

    @Autowired
    private RegionJpaRepository regionRepository;

    @Autowired
    private CityJpaRepository cityRepository;

    @Autowired
    private CityDistrictJpaRepository cityDistrictRepository;

    @Autowired
    private StreetJpaRepository streetRepository;

    @Autowired
    private AddressJpaRepository addressRepository;

    @Test
    void validAddressPersistsWithCompositeForeignKeys() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        CityDistrict district = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));

        Address saved = addressPort.save(
                Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1")));

        assertEquals(street.id(), saved.street().id());
        assertEquals(district.id(), saved.cityDistrict().id());
    }

    @Test
    void cityDistrictFromDifferentCityIsRejectedByDatabase() {
        RegionEntity region = regionRepository.saveAndFlush(newRegionEntity());
        CityEntity cityA = cityRepository.saveAndFlush(newCityEntity(region, "Омск"));
        CityEntity cityB = cityRepository.saveAndFlush(newCityEntity(region, "Тара"));
        StreetEntity streetA = streetRepository.saveAndFlush(newStreetEntity(cityA));
        CityDistrictEntity districtB = cityDistrictRepository.saveAndFlush(newCityDistrictEntity(cityB));

        AddressEntity invalid = new AddressEntity();
        invalid.setId(UUID.randomUUID());
        invalid.setCity(cityA);
        invalid.setStreet(streetA);
        invalid.setCityDistrict(districtB);
        invalid.setHouseNumber("15");
        invalid.setHouseAddition(null);
        invalid.setCanonicalHouse("15");

        assertThrows(DataIntegrityViolationException.class,
                () -> addressRepository.saveAndFlush(invalid));
    }

    private RegionEntity newRegionEntity() {
        RegionEntity entity = new RegionEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Омская область");
        return entity;
    }

    private CityEntity newCityEntity(RegionEntity region, String name) {
        CityEntity entity = new CityEntity();
        entity.setId(UUID.randomUUID());
        entity.setRegion(region);
        entity.setName(name);
        return entity;
    }

    private StreetEntity newStreetEntity(CityEntity city) {
        StreetEntity entity = new StreetEntity();
        entity.setId(UUID.randomUUID());
        entity.setCity(city);
        entity.setType(StreetType.STREET);
        entity.setCanonicalName("Ленина");
        return entity;
    }

    private CityDistrictEntity newCityDistrictEntity(CityEntity city) {
        CityDistrictEntity entity = new CityDistrictEntity();
        entity.setId(UUID.randomUUID());
        entity.setCity(city);
        entity.setName("Центральный");
        return entity;
    }
}