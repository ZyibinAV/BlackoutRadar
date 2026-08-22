package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionTransformerStationJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SubscriptionPersistenceTest {

    @Autowired
    private UserPort userPort;

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
    private SubscriptionPort subscriptionPort;

    @Autowired
    private SubscriptionTransformerStationJpaRepository stationAssociationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFindByIdWithoutStationsRoundTrip() {
        User user = saveUser();
        Address address = saveAddress();

        Subscription saved = saveSubscription(user, address, Set.of());

        Optional<Subscription> found = subscriptionPort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
        assertEquals(user.id(), found.get().user().id());
        assertEquals(address.id(), found.get().address().id());
        assertEquals(saved.monitoringStart(), found.get().monitoringStart());
        assertEquals(saved.monitoringEnd(), found.get().monitoringEnd());
        assertEquals(saved.isActive(), found.get().isActive());
        assertEquals(saved.serviceAccessUntil(), found.get().serviceAccessUntil());
        assertTrue(found.get().transformerStations().isEmpty());
    }

    @Test
    void saveAndFindByIdWithStationsRoundTrip() {
        User user = saveUser();
        Address address = saveAddress();
        TransformerStation stationA = saveStation("ТП-101");
        TransformerStation stationB = saveStation("ТП-102");

        Subscription saved = saveSubscription(user, address, Set.of(stationA, stationB));

        Optional<Subscription> found = subscriptionPort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(2, found.get().transformerStations().size());
        assertEquals(Set.of(stationA.id(), stationB.id()),
                Set.copyOf(found.get().transformerStations().stream().map(TransformerStation::id).toList()));
    }

    @Test
    void inactiveSubscriptionPreserved() {
        User user = saveUser();
        Address address = saveAddress();
        Instant start = Instant.now();

        Subscription saved = subscriptionPort.save(Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), false, start.plus(365, ChronoUnit.DAYS)));

        Optional<Subscription> found = subscriptionPort.findById(saved.id());

        assertTrue(found.isPresent());
        assertTrue(!found.get().isActive());
        assertEquals(saved.serviceAccessUntil(), found.get().serviceAccessUntil());
    }

    @Test
    void updateReplacesStationSet() {
        User user = saveUser();
        Address address = saveAddress();
        TransformerStation stationA = saveStation("ТП-101");
        TransformerStation stationB = saveStation("ТП-102");
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();

        subscriptionPort.save(Subscription.of(id, user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS), Set.of(stationA)));

        subscriptionPort.save(Subscription.of(id, user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS), Set.of(stationB)));

        Optional<Subscription> found = subscriptionPort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(1, found.get().transformerStations().size());
        assertEquals(Set.of(stationB.id()),
                Set.copyOf(found.get().transformerStations().stream().map(TransformerStation::id).toList()));
    }

    @Test
    void resavingSameStationsDoesNotCreateDuplicates() {
        User user = saveUser();
        Address address = saveAddress();
        TransformerStation stationA = saveStation("ТП-101");
        TransformerStation stationB = saveStation("ТП-102");
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();

        subscriptionPort.save(Subscription.of(id, user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS), Set.of(stationA, stationB)));
        subscriptionPort.save(Subscription.of(id, user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS), Set.of(stationA, stationB)));

        Optional<Subscription> found = subscriptionPort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(2, found.get().transformerStations().size());
    }

    @Test
    void saveReplacesUserAndAddress() {
        User userA = saveUser("a@example.com");
        User userB = saveUser("b@example.com");
        Address addressA = saveAddress("Омская область");
        Address addressB = saveAddress("Новосибирская область");
        TransformerStation station = saveStation("ТП-101");
        UUID id = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plus(30, ChronoUnit.DAYS);
        Instant serviceAccessUntil = start.plus(365, ChronoUnit.DAYS);

        subscriptionPort.save(Subscription.of(id, userA, addressA, start, end, true,
                serviceAccessUntil, Set.of(station)));

        subscriptionPort.save(Subscription.of(id, userB, addressB, start, end, true,
                serviceAccessUntil, Set.of(station)));

        entityManager.flush();
        entityManager.clear();

        Optional<Subscription> found = subscriptionPort.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(userB.id(), found.get().user().id());
        assertEquals(addressB.id(), found.get().address().id());
        assertInstantClose(start, found.get().monitoringStart());
        assertInstantClose(end, found.get().monitoringEnd());
        assertTrue(found.get().isActive());
        assertInstantClose(serviceAccessUntil, found.get().serviceAccessUntil());
        assertEquals(Set.of(station.id()),
                Set.copyOf(found.get().transformerStations().stream().map(TransformerStation::id).toList()));
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(subscriptionPort.findById(UUID.randomUUID()).isEmpty());
    }

    private User saveUser() {
        return saveUser("ivan@example.com");
    }

    private User saveUser(String email) {
        return userPort.save(User.of(UUID.randomUUID(), email, UserRole.USER, true));
    }

    private Address saveAddress() {
        return saveAddress("Омская область");
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

    private Subscription saveSubscription(User user, Address address, Set<TransformerStation> stations) {
        Instant start = Instant.now();
        return subscriptionPort.save(Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS),
                new LinkedHashSet<>(stations)));
    }

    private void assertInstantClose(Instant expected, Instant actual) {
        assertTrue(Duration.between(expected, actual).abs().toNanos() <= 1_000_000L,
                () -> "expected " + expected + " but was " + actual);
    }
}