package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityDistrictJpaRepository;
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
class CityDistrictPersistenceTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private CityDistrictPort cityDistrictPort;

    @Autowired
    private CityDistrictJpaRepository cityDistrictRepository;

    @Test
    void saveAndFindByCityAndNameRoundTrip() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        UUID id = UUID.randomUUID();

        CityDistrict saved = cityDistrictPort.save(CityDistrict.of(id, city, "Центральный"));

        assertEquals(id, saved.id());

        Optional<CityDistrict> found = cityDistrictPort.findByCityAndName(city, "Центральный");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(city.id(), found.get().city().id());
        assertEquals("Центральный", found.get().name());
    }

    @Test
    void sameNameInDifferentCitiesIsAllowed() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City cityA = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        City cityB = cityPort.save(City.of(UUID.randomUUID(), region, "Тара"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        cityDistrictPort.save(CityDistrict.of(idA, cityA, "Центральный"));
        cityDistrictPort.save(CityDistrict.of(idB, cityB, "Центральный"));

        assertEquals(idA, cityDistrictPort.findByCityAndName(cityA, "Центральный").orElseThrow().id());
        assertEquals(idB, cityDistrictPort.findByCityAndName(cityB, "Центральный").orElseThrow().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));

        assertTrue(cityDistrictPort.findByCityAndName(city, "Несуществующий район").isEmpty());
    }

    @Test
    void duplicateInSameCityIsRejected() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
            cityDistrictRepository.flush();
        });
    }
}