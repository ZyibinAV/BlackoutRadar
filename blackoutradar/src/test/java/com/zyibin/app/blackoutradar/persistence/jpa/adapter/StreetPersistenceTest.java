package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.StreetJpaRepository;
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
class StreetPersistenceTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private StreetPort streetPort;

    @Autowired
    private StreetJpaRepository streetRepository;

    @Test
    void saveAndFindByCityTypeAndCanonicalNameRoundTrip() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        UUID id = UUID.randomUUID();

        Street saved = streetPort.save(Street.of(id, city, StreetType.STREET, "Ленина"));

        assertEquals(id, saved.id());

        Optional<Street> found = streetPort.findByCityAndTypeAndCanonicalName(city, StreetType.STREET, "Ленина");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(city.id(), found.get().city().id());
        assertEquals(StreetType.STREET, found.get().type());
        assertEquals("Ленина", found.get().canonicalName());
    }

    @Test
    void sameNameInDifferentCitiesIsAllowed() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City cityA = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        City cityB = cityPort.save(City.of(UUID.randomUUID(), region, "Тара"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        streetPort.save(Street.of(idA, cityA, StreetType.STREET, "Ленина"));
        streetPort.save(Street.of(idB, cityB, StreetType.STREET, "Ленина"));

        assertEquals(idA, streetPort.findByCityAndTypeAndCanonicalName(cityA, StreetType.STREET, "Ленина")
                .orElseThrow().id());
        assertEquals(idB, streetPort.findByCityAndTypeAndCanonicalName(cityB, StreetType.STREET, "Ленина")
                .orElseThrow().id());
    }

    @Test
    void sameNameWithDifferentTypeInSameCityIsAllowed() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        streetPort.save(Street.of(idA, city, StreetType.STREET, "Мира"));
        streetPort.save(Street.of(idB, city, StreetType.PROSPECT, "Мира"));

        assertEquals(idA, streetPort.findByCityAndTypeAndCanonicalName(city, StreetType.STREET, "Мира")
                .orElseThrow().id());
        assertEquals(idB, streetPort.findByCityAndTypeAndCanonicalName(city, StreetType.PROSPECT, "Мира")
                .orElseThrow().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));

        assertTrue(streetPort.findByCityAndTypeAndCanonicalName(city, StreetType.STREET, "Несуществующая улица")
                .isEmpty());
    }

    @Test
    void duplicateInSameCityWithSameTypeAndCanonicalNameIsRejected() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
            streetRepository.flush();
        });
    }
}