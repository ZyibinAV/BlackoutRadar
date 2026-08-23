package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CanonicalResolutionPersistenceTest {

    @Autowired private RegionPort regionPort;
    @Autowired private RegionalDistrictPort regionalDistrictPort;
    @Autowired private CityPort cityPort;
    @Autowired private CityDistrictPort cityDistrictPort;
    @Autowired private StreetPort streetPort;
    @Autowired private AddressPort addressPort;

    @Test
    void regionFirstAndRepeatedReturnsSame() {
        Region first = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        Region second = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        assertEquals(first.id(), second.id());
        assertEquals("ОМСКАЯ ОБЛАСТЬ", first.name());
    }

    @Test
    void regionDifferentIdentityCreatesSeparateRow() {
        Region r1 = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        Region r2 = regionPort.resolveCanonical("НОВОСИБИРСКАЯ ОБЛАСТЬ");
        assertNotEquals(r1.id(), r2.id());
    }

    @Test
    void regionSaveStillWorks() {
        Region saved = regionPort.save(Region.of(UUID.randomUUID(), "ТОМСКАЯ ОБЛАСТЬ"));
        assertNotNull(saved.id());
        assertEquals("ТОМСКАЯ ОБЛАСТЬ", saved.name());
    }

    @Test
    void regionalDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        RegionalDistrict first = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "ОМСКИЙ РАЙОН");
        RegionalDistrict second = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "ОМСКИЙ РАЙОН");
        assertEquals(first.id(), second.id());
    }

    @Test
    void regionalDistrictDifferentIdentity() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        RegionalDistrict r1 = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "РАЙОН А");
        RegionalDistrict r2 = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "РАЙОН Б");
        assertNotEquals(r1.id(), r2.id());
        RegionalDistrict r3 = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.URBAN_OKRUG, "РАЙОН А");
        assertNotEquals(r1.id(), r3.id());
    }

    @Test
    void cityWithoutDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City first = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        City second = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        assertEquals(first.id(), second.id());
        assertEquals("ОМСК", first.name());
        assertEquals(region.id(), first.region().id());
    }

    @Test
    void cityWithDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        RegionalDistrict rd = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "РАЙОН");
        City first = cityPort.resolveCanonicalInRegionalDistrict(rd, "ГОРОД");
        City second = cityPort.resolveCanonicalInRegionalDistrict(rd, "ГОРОД");
        assertEquals(first.id(), second.id());
    }

    @Test
    void cityDifferentIdentities() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City c1 = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        City c2 = cityPort.resolveCanonicalInRegion(region, "НОВОСИБИРСК");
        assertNotEquals(c1.id(), c2.id());
        // same name but under different regionalDistrict -> different
        RegionalDistrict rd = regionalDistrictPort.resolveCanonical(region, RegionalDistrictType.MUNICIPAL_DISTRICT, "РАЙОН");
        City c3 = cityPort.resolveCanonicalInRegionalDistrict(rd, "ОМСК");
        assertNotEquals(c1.id(), c3.id());
    }

    @Test
    void cityDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        CityDistrict first = cityDistrictPort.resolveCanonical(city, "ЦЕНТРАЛЬНЫЙ");
        CityDistrict second = cityDistrictPort.resolveCanonical(city, "ЦЕНТРАЛЬНЫЙ");
        assertEquals(first.id(), second.id());
    }

    @Test
    void cityDistrictDifferentIdentity() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        CityDistrict d1 = cityDistrictPort.resolveCanonical(city, "ЦЕНТРАЛЬНЫЙ");
        CityDistrict d2 = cityDistrictPort.resolveCanonical(city, "КИРОВСКИЙ");
        assertNotEquals(d1.id(), d2.id());
    }

    @Test
    void streetFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        Street first = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        Street second = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        assertEquals(first.id(), second.id());
        assertEquals(StreetType.STREET, first.type());
    }

    @Test
    void streetDifferentIdentity() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        Street s1 = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        Street s2 = streetPort.resolveCanonical(city, StreetType.STREET, "МИРА");
        assertNotEquals(s1.id(), s2.id());
        Street s3 = streetPort.resolveCanonical(city, StreetType.PROSPECT, "ЛЕНИНА");
        assertNotEquals(s1.id(), s3.id());
    }

    @Test
    void addressWithoutDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        Street street = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        House house = new House("15", null, "15");
        Address first = addressPort.resolveCanonical(street, house);
        Address second = addressPort.resolveCanonical(street, house);
        assertEquals(first.id(), second.id());
        assertEquals("15", first.house().canonicalHouse());
    }

    @Test
    void addressWithDistrictFirstAndRepeated() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        CityDistrict district = cityDistrictPort.resolveCanonical(city, "ЦЕНТРАЛЬНЫЙ");
        Street street = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        House house = new House("15", "К1", "15К1");
        Address first = addressPort.resolveCanonical(street, district, house);
        Address second = addressPort.resolveCanonical(street, district, house);
        assertEquals(first.id(), second.id());
    }

    @Test
    void addressDifferentIdentities() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        Street street = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        CityDistrict d1 = cityDistrictPort.resolveCanonical(city, "ЦЕНТРАЛЬНЫЙ");
        CityDistrict d2 = cityDistrictPort.resolveCanonical(city, "КИРОВСКИЙ");
        House h1 = new House("15", null, "15");
        House h2 = new House("16", null, "16");
        Address a1 = addressPort.resolveCanonical(street, h1);
        Address a2 = addressPort.resolveCanonical(street, h2);
        assertNotEquals(a1.id(), a2.id());
        Address a3 = addressPort.resolveCanonical(street, d1, h1);
        Address a4 = addressPort.resolveCanonical(street, d2, h1);
        assertNotEquals(a3.id(), a4.id());
        // same street+house but with vs without district are different
        assertNotEquals(a1.id(), a3.id());
    }

    @Test
    void addressMappingPreservesCityId() {
        Region region = regionPort.resolveCanonical("ОМСКАЯ ОБЛАСТЬ");
        City city = cityPort.resolveCanonicalInRegion(region, "ОМСК");
        Street street = streetPort.resolveCanonical(city, StreetType.STREET, "ЛЕНИНА");
        House house = new House("15", null, "15");
        Address address = addressPort.resolveCanonical(street, house);
        assertEquals(street.id(), address.street().id());
        assertEquals(city.id(), address.street().city().id());
    }

    @Test
    void existingSaveStillWorksForAll() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "САХАЛИН"));
        assertNotNull(region.id());
        RegionalDistrict rd = regionalDistrictPort.save(RegionalDistrict.of(UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "РАЙОН SAVE"));
        assertNotNull(rd.id());
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "ГОРОД SAVE"));
        assertNotNull(city.id());
        CityDistrict cd = cityDistrictPort.save(CityDistrict.of(UUID.randomUUID(), city, "РАЙОН SAVE"));
        assertNotNull(cd.id());
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "УЛИЦА SAVE"));
        assertNotNull(street.id());
        Address address = addressPort.save(Address.of(UUID.randomUUID(), street, new House("99", null, "99")));
        assertNotNull(address.id());
    }
}
