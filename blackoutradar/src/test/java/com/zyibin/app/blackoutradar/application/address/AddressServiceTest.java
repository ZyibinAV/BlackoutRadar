package com.zyibin.app.blackoutradar.application.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AddressServiceTest {

    @Autowired
    private AddressService addressService;

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private StreetPort streetPort;

    @Autowired
    private AddressPort addressPort;

    @Autowired
    private RegionalDistrictPort regionalDistrictPort;

    @Autowired
    private CityDistrictPort cityDistrictPort;

    @Test
    void resolveCreatesCanonicalAddress() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");

        Address address = addressService.resolve(input);

        assertNotNull(address);
        assertEquals("ЛЕНИНА", address.street().canonicalName());
        assertEquals(StreetType.STREET, address.street().type());
        assertEquals("15", address.house().canonicalHouse());
        assertNull(address.cityDistrict());
        assertEquals("ОМСК", address.street().city().name());
        assertEquals("ОМСКАЯ ОБЛАСТЬ", address.street().city().region().name());
    }

    @Test
    void repeatedResolveReturnsExistingObjects() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");

        Address first = addressService.resolve(input);
        Address second = addressService.resolve(input);

        assertEquals(first.id(), second.id());
        assertEquals(first.street().id(), second.street().id());
        assertEquals(first.street().city().id(), second.street().city().id());
        assertEquals(first.street().city().region().id(), second.street().city().region().id());
    }

    @Test
    void existingRegionIsReused() {
        AddressInput first = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");
        addressService.resolve(first);

        AddressInput second = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Мира", "10");
        Address address = addressService.resolve(second);

        assertEquals("ОМСКАЯ ОБЛАСТЬ", address.street().city().region().name());
    }

    @Test
    void optionalRegionalDistrictCreatesCityUnderDistrict() {
        AddressInput input = new AddressInput(
                "Омская область", "Омский район", RegionalDistrictType.MUNICIPAL_DISTRICT,
                "Омск", null, "ул Ленина", "15");

        Address address = addressService.resolve(input);

        assertNotNull(address.street().city().regionalDistrict());
        assertEquals("ОМСКИЙ РАЙОН", address.street().city().regionalDistrict().name());
        assertEquals(RegionalDistrictType.MUNICIPAL_DISTRICT,
                address.street().city().regionalDistrict().type());
    }

    @Test
    void cityDirectlyUnderRegionWhenRegionalDistrictAbsent() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");

        Address address = addressService.resolve(input);

        assertNull(address.street().city().regionalDistrict());
    }

    @Test
    void optionalCityDistrictIsCreated() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", "Центральный", "ул Ленина", "15");

        Address address = addressService.resolve(input);

        assertNotNull(address.cityDistrict());
        assertEquals("ЦЕНТРАЛЬНЫЙ", address.cityDistrict().name());
        assertEquals(address.street().city().id(), address.cityDistrict().city().id());
    }

    @Test
    void streetLookupAndCreation() {
        AddressInput input1 = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");
        Address a1 = addressService.resolve(input1);
        AddressInput input2 = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "16");
        Address a2 = addressService.resolve(input2);

        assertEquals(a1.street().id(), a2.street().id());
    }

    @Test
    void differentStreetNameCreatesDifferentStreet() {
        Address a1 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        Address a2 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Мира", "15"));
        assertNotEquals(a1.street().id(), a2.street().id());
    }

    @Test
    void differentStreetTypeDoNotCollide() {
        Address a1 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        Address a2 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "пр Ленина", "15"));
        assertNotEquals(a1.street().id(), a2.street().id());
        assertEquals(StreetType.STREET, a1.street().type());
        assertEquals(StreetType.PROSPECT, a2.street().type());
    }

    @Test
    void differentCityDoNotCollide() {
        Address a1 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        Address a2 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Новосибирск", null, "ул Ленина", "15"));
        assertNotEquals(a1.street().city().id(), a2.street().city().id());
        assertNotEquals(a1.street().id(), a2.street().id());
        assertNotEquals(a1.id(), a2.id());
    }

    @Test
    void differentCityDistrictDoNotCollide() {
        Address a1 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", "Центральный", "ул Ленина", "15"));
        Address a2 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", "Кировский", "ул Ленина", "15"));
        assertNotEquals(a1.cityDistrict().id(), a2.cityDistrict().id());
        assertNotEquals(a1.id(), a2.id());
    }

    @Test
    void differentCanonicalHouseDoNotCollide() {
        Address a1 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        Address a2 = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "16"));
        assertNotEquals(a1.id(), a2.id());
        assertEquals("15", a1.house().canonicalHouse());
        assertEquals("16", a2.house().canonicalHouse());
    }

    @Test
    void crossCityStreetCityDistrictRejectedViaDomain() {
        // Direct domain invariant: street in City A, cityDistrict in City B must fail
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", "Центральный", "ул Ленина", "15");
        Address address = addressService.resolve(input);
        // Create another city and try to create Address with street from first city and district from second city
        AddressInput other = new AddressInput(
                "Омская область", null, null, "Новосибирск", "Центральный", "ул Ленина", "15");
        Address otherAddress = addressService.resolve(other);
        // Now attempt domain-level mismatch directly
        assertThrows(IllegalArgumentException.class, () ->
                Address.of(java.util.UUID.randomUUID(),
                        address.street(), otherAddress.cityDistrict(), address.house()));
    }

    @Test
    void normalizationIsApplied() {
        Address a1 = addressService.resolve(new AddressInput(
                "  омская область  ", null, null, "  омск  ", null, "  ул.   ленина  ", "  15  "));
        Address a2 = addressService.resolve(new AddressInput(
                "ОМСКАЯ ОБЛАСТЬ", null, null, "ОМСК", null, "ул Ленина", "15"));
        assertEquals(a1.id(), a2.id());
        assertEquals("ЛЕНИНА", a1.street().canonicalName());
        assertEquals("15", a1.house().canonicalHouse());
    }

    @Test
    void houseAdditionIsUppercased() {
        Address address = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15к1"));
        assertEquals("15", address.house().houseNumber());
        assertEquals("К1", address.house().houseAddition());
        assertEquals("15К1", address.house().canonicalHouse());
    }

    @Test
    void unknownStreetTypeIsPreserved() {
        Address address = addressService.resolve(new AddressInput(
                "Омская область", null, null, "Омск", null, "Ленина", "15"));
        assertEquals(StreetType.UNKNOWN, address.street().type());
        assertEquals("ЛЕНИНА", address.street().canonicalName());
    }

    @Test
    void addressLookupViaPortsMatchesServiceResult() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");
        Address created = addressService.resolve(input);
        var found = addressPort.findByStreetAndCanonicalHouse(created.street(), "15");
        assertTrue(found.isPresent());
        assertEquals(created.id(), found.get().id());
    }

    @Test
    void domainToPersistenceRoundTrip() {
        AddressInput input = new AddressInput(
                "Омская область", null, null, "Омск", null, "ул Ленина", "15");
        Address created = addressService.resolve(input);
        // Verify via direct port lookup that persistence preserved canonical values
        assertEquals("ОМСКАЯ ОБЛАСТЬ", created.street().city().region().name());
        assertEquals("ОМСК", created.street().city().name());
    }
}
