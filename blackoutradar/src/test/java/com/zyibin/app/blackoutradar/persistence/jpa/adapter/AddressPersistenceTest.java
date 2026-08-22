package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.zyibin.app.blackoutradar.persistence.jpa.repository.AddressJpaRepository;
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
class AddressPersistenceTest {

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
    private AddressJpaRepository addressRepository;

    @Test
    void saveAndFindWithoutCityDistrictRoundTrip() {
        City city = saveCity();
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        UUID id = UUID.randomUUID();

        Address saved = addressPort.save(Address.of(id, street, new House("15", null, "15")));

        assertEquals(id, saved.id());

        Optional<Address> found = addressPort.findByStreetAndCanonicalHouse(street, "15");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(street.id(), found.get().street().id());
        assertTrue(found.get().cityDistrict() == null);
        assertEquals(new House("15", null, "15"), found.get().house());
    }

    @Test
    void saveAndFindWithCityDistrictRoundTrip() {
        City city = saveCity();
        CityDistrict district = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        UUID id = UUID.randomUUID();

        Address saved = addressPort.save(
                Address.of(id, street, district, new House("15", "к1", "15к1")));

        assertEquals(id, saved.id());

        Optional<Address> found = addressPort.findByStreetAndCityDistrictAndCanonicalHouse(street, district, "15к1");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(street.id(), found.get().street().id());
        assertEquals(district.id(), found.get().cityDistrict().id());
        assertEquals(new House("15", "к1", "15к1"), found.get().house());
    }

    @Test
    void sameCanonicalHouseOnDifferentStreetsIsAllowed() {
        City city = saveCity();
        Street streetA = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        Street streetB = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Мира"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        addressPort.save(Address.of(idA, streetA, new House("15", null, "15")));
        addressPort.save(Address.of(idB, streetB, new House("15", null, "15")));

        assertEquals(idA, addressPort.findByStreetAndCanonicalHouse(streetA, "15").orElseThrow().id());
        assertEquals(idB, addressPort.findByStreetAndCanonicalHouse(streetB, "15").orElseThrow().id());
    }

    @Test
    void sameCanonicalHouseOnSameStreetWithDifferentCityDistrictsIsAllowed() {
        City city = saveCity();
        CityDistrict districtA = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
        CityDistrict districtB = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Кировский"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        addressPort.save(Address.of(idA, street, districtA, new House("15", null, "15")));
        addressPort.save(Address.of(idB, street, districtB, new House("15", null, "15")));

        assertEquals(idA, addressPort.findByStreetAndCityDistrictAndCanonicalHouse(street, districtA, "15")
                .orElseThrow().id());
        assertEquals(idB, addressPort.findByStreetAndCityDistrictAndCanonicalHouse(street, districtB, "15")
                .orElseThrow().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        City city = saveCity();
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));

        assertTrue(addressPort.findByStreetAndCanonicalHouse(street, "999").isEmpty());
    }

    private City saveCity() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        return cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
    }

    @Test
    void duplicateWithoutCityDistrictIsRejected() {
        City city = saveCity();
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        addressPort.save(Address.of(UUID.randomUUID(), street, new House("15", null, "15")));

        assertThrows(DataIntegrityViolationException.class, () -> {
            addressPort.save(Address.of(UUID.randomUUID(), street, new House("15", null, "15")));
            addressRepository.flush();
        });
    }

    @Test
    void duplicateWithCityDistrictIsRejected() {
        City city = saveCity();
        CityDistrict district = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "Центральный"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        addressPort.save(Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1")));

        assertThrows(DataIntegrityViolationException.class, () -> {
            addressPort.save(Address.of(UUID.randomUUID(), street, district, new House("15", "к1", "15к1")));
            addressRepository.flush();
        });
    }
}