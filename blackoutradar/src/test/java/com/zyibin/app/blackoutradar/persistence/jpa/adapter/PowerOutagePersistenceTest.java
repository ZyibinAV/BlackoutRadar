package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PowerOutagePersistenceTest {

    @Autowired
    private SourcePort sourcePort;

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private CityPort cityPort;

    @Autowired
    private StreetPort streetPort;

    @Autowired
    private AddressPort addressPort;

    @Autowired
    private TransformerStationPort stationPort;

    @Autowired
    private PowerOutagePort powerOutagePort;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void saveAndFindByIdRoundTripWithTransformerStation() {
        Source source = saveSource("Горэлектросеть");
        Address address = saveAddress("Омская область");
        TransformerStation station = saveStation("ТП-101");

        PowerOutage saved = savePowerOutage(source, address, station);

        Optional<PowerOutage> found = powerOutagePort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
        assertEquals(source.id(), found.get().source().id());
        assertEquals(saved.startTime(), found.get().startTime());
        assertEquals(saved.endTime(), found.get().endTime());
        assertEquals(saved.reason(), found.get().reason());
        assertEquals(saved.status(), found.get().status());
        assertEquals(1, found.get().addresses().size());
        PowerOutageAddress restoredAddress = found.get().addresses().iterator().next();
        assertEquals(address.id(), restoredAddress.address().id());
        assertEquals(station.id(), restoredAddress.transformerStation().id());
    }

    @Test
    void saveAndFindByIdRoundTripWithoutTransformerStation() {
        Source source = saveSource("Горэлектросеть");
        Address address = saveAddress("Омская область");

        PowerOutage saved = savePowerOutage(source, address, null);

        Optional<PowerOutage> found = powerOutagePort.findById(saved.id());

        assertTrue(found.isPresent());
        PowerOutageAddress restoredAddress = found.get().addresses().iterator().next();
        assertEquals(address.id(), restoredAddress.address().id());
        assertNull(restoredAddress.transformerStation());
    }

    @Test
    void saveAndFindByIdWithMultipleAddresses() {
        Source source = saveSource("Горэлектросеть");
        Address addressA = saveAddress("Омская область");
        Address addressB = saveAddress("Новосибирская область");
        TransformerStation station = saveStation("ТП-101");

        PowerOutageAddress poaA = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressA);
        PowerOutageAddress poaB = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressB, station);
        PowerOutage powerOutage = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное отключение", "АКТИВНО", new LinkedHashSet<>(Set.of(poaA, poaB)));

        PowerOutage saved = powerOutagePort.save(powerOutage);

        Optional<PowerOutage> found = powerOutagePort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(2, found.get().addresses().size());
        assertEquals(Set.of(addressA.id(), addressB.id()), found.get().addresses().stream()
                .map(a -> a.address().id()).collect(Collectors.toSet()));
    }

    @Test
    void updateReplacesAddresses() {
        Source source = saveSource("Горэлектросеть");
        Address addressA = saveAddress("Омская область");
        Address addressB = saveAddress("Новосибирская область");
        UUID id = UUID.randomUUID();

        savePowerOutageWithAddresses(id, source, addressA, null);
        entityManager.flush();
        entityManager.clear();

        savePowerOutageWithAddresses(id, source, addressB, null);
        entityManager.flush();
        entityManager.clear();

        Optional<PowerOutage> found = powerOutagePort.findById(id);

        assertTrue(found.isPresent());
        // Aggregate reconstruction returns actual state after replacement
        assertEquals(id, found.get().id());
        // Count matches expected
        assertEquals(1, found.get().addresses().size());
        Set<UUID> addressIds = found.get().addresses().stream()
                .map(a -> a.address().id())
                .collect(Collectors.toSet());
        // New associations present
        assertTrue(addressIds.contains(addressB.id()));
        // Old associations absent
        assertFalse(addressIds.contains(addressA.id()));
        // No duplicate associations
        assertEquals(1, addressIds.size());
        assertEquals(addressB.id(), found.get().addresses().iterator().next().address().id());
    }

    @Test
    void resavingSameAddressesDoesNotCreateDuplicates() {
        Source source = saveSource("Горэлектросеть");
        Address address = saveAddress("Омская область");
        UUID id = UUID.randomUUID();

        savePowerOutageWithAddresses(id, source, address, null);
        entityManager.flush();
        entityManager.clear();

        savePowerOutageWithAddresses(id, source, address, null);
        entityManager.flush();
        entityManager.clear();

        Optional<PowerOutage> found = powerOutagePort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(1, found.get().addresses().size());
        Set<UUID> addressIds = found.get().addresses().stream()
                .map(a -> a.address().id())
                .collect(Collectors.toSet());
        // Returns expected set
        assertEquals(Set.of(address.id()), addressIds);
        // Collection composition unchanged
        assertEquals(1, addressIds.size());
        assertEquals(address.id(), found.get().addresses().iterator().next().address().id());
        // Aggregate reconstruction preserves outage identity
        assertEquals(id, found.get().id());
    }

    @Test
    void updatePreservesSourceAndTimes() {
        Source sourceA = saveSource("Горэлектросеть");
        Source sourceB = saveSource("Новосибирскэнерго");
        Address address = saveAddress("Омская область");
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");

        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage first = PowerOutage.of(id, sourceA, start, end, "Аварийное отключение", "АКТИВНО",
                Set.of(poa));
        powerOutagePort.save(first);
        entityManager.flush();
        entityManager.clear();

        PowerOutageAddress poa2 = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage second = PowerOutage.of(id, sourceB, start, end, "Плановое отключение", "ЗАВЕРШЕНО",
                Set.of(poa2));
        powerOutagePort.save(second);
        entityManager.flush();
        entityManager.clear();

        Optional<PowerOutage> found = powerOutagePort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(sourceB.id(), found.get().source().id());
        assertEquals("Плановое отключение", found.get().reason());
        assertEquals("ЗАВЕРШЕНО", found.get().status());
        assertEquals(start, found.get().startTime());
        assertEquals(end, found.get().endTime());
        // Aggregate reconstruction with actual addresses after independent read
        assertEquals(1, found.get().addresses().size());
        assertEquals(address.id(), found.get().addresses().iterator().next().address().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(powerOutagePort.findById(UUID.randomUUID()).isEmpty());
    }

    private PowerOutage savePowerOutage(Source source, Address address, TransformerStation stationOrNull) {
        PowerOutageAddress poa = stationOrNull != null
                ? PowerOutageAddress.unboundOf(UUID.randomUUID(), address, stationOrNull)
                : PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage powerOutage = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное отключение", "АКТИВНО", Set.of(poa));
        return powerOutagePort.save(powerOutage);
    }

    private PowerOutage savePowerOutageWithAddresses(UUID id, Source source, Address address,
                                                     TransformerStation stationOrNull) {
        PowerOutageAddress poa = stationOrNull != null
                ? PowerOutageAddress.unboundOf(UUID.randomUUID(), address, stationOrNull)
                : PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage powerOutage = PowerOutage.of(id, source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное отключение", "АКТИВНО", Set.of(poa));
        return powerOutagePort.save(powerOutage);
    }

    private Source saveSource(String name) {
        return sourcePort.save(Source.of(UUID.randomUUID(), name, "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true));
    }

    private Address saveAddress(String regionName) {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), regionName));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Омск"));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина"));
        return addressPort.save(Address.of(UUID.randomUUID(), street, new House("15", null, "15")));
    }

    private TransformerStation saveStation(String name) {
        return stationPort.save(TransformerStation.of(UUID.randomUUID(), name));
    }
}
