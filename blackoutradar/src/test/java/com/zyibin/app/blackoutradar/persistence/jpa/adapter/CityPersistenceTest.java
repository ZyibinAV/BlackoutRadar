package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityJpaRepository;
import java.util.Optional;
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
class CityPersistenceTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private RegionalDistrictPort regionalDistrictPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private CityJpaRepository cityRepository;

    @Test
    void saveAndFindWithoutRegionalDistrictRoundTrip() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        UUID id = UUID.randomUUID();

        City saved = cityPort.save(City.of(id, region, "Омск"));

        assertEquals(id, saved.id());

        Optional<City> found = cityPort.findByRegionAndName(region, "Омск");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(region.id(), found.get().region().id());
        assertTrue(found.get().regionalDistrict() == null);
        assertEquals("Омск", found.get().name());
    }

    @Test
    void saveAndFindWithRegionalDistrictRoundTrip() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        RegionalDistrict district = regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));
        UUID id = UUID.randomUUID();

        City saved = cityPort.save(City.of(id, region, district, "Лузино"));

        assertEquals(id, saved.id());

        Optional<City> found = cityPort.findByRegionAndRegionalDistrictAndName(region, district, "Лузино");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(district.id(), found.get().regionalDistrict().id());
        assertEquals("Лузино", found.get().name());
    }

    @Test
    void sameNameInDifferentRegionsIsAllowed() {
        Region regionA = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        Region regionB = regionPort.save(Region.of(UUID.randomUUID(), "Новосибирская область"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        cityPort.save(City.of(idA, regionA, "Дубки"));
        cityPort.save(City.of(idB, regionB, "Дубки"));

        assertEquals(idA, cityPort.findByRegionAndName(regionA, "Дубки").orElseThrow().id());
        assertEquals(idB, cityPort.findByRegionAndName(regionB, "Дубки").orElseThrow().id());
    }

    @Test
    void sameNameInDifferentRegionalDistrictsOfSameRegionIsAllowed() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        RegionalDistrict districtA = regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));
        RegionalDistrict districtB = regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_OKRUG, "Тарский район"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        cityPort.save(City.of(idA, region, districtA, "Новотроицк"));
        cityPort.save(City.of(idB, region, districtB, "Новотроицк"));

        assertEquals(idA, cityPort.findByRegionAndRegionalDistrictAndName(region, districtA, "Новотроицк")
                .orElseThrow().id());
        assertEquals(idB, cityPort.findByRegionAndRegionalDistrictAndName(region, districtB, "Новотроицк")
                .orElseThrow().id());
    }

    @Test
    void cityWithoutDistrictIsNotFoundByLookupWithDistrictAndViceVersa() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        RegionalDistrict district = regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));

        cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        cityPort.save(City.of(UUID.randomUUID(), region, district, "Лузино"));

        assertTrue(cityPort.findByRegionAndRegionalDistrictAndName(region, district, "Омск").isEmpty());
        assertTrue(cityPort.findByRegionAndName(region, "Лузино").isEmpty());
    }

    @Test
    void findAbsentReturnsEmpty() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));

        assertTrue(cityPort.findByRegionAndName(region, "Несуществующий город").isEmpty());
    }

    @Test
    void duplicateWithoutDistrictInSameRegionIsRejected() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
            cityRepository.flush();
        });
    }

    @Test
    void duplicateInSameRegionalDistrictIsRejected() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        RegionalDistrict district = regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));
        cityPort.save(City.of(UUID.randomUUID(), region, district, "Лузино"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            cityPort.save(City.of(UUID.randomUUID(), region, district, "Лузино"));
            cityRepository.flush();
        });
    }
}