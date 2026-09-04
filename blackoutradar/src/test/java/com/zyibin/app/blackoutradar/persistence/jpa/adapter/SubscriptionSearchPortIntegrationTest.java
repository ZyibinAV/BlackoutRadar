package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
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
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionSearchPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SubscriptionSearchPortIntegrationTest {

    @Autowired private SubscriptionSearchPort searchPort;
    @Autowired private SubscriptionPort subscriptionPort;
    @Autowired private UserPort userPort;
    @Autowired private RegionPort regionPort;
    @Autowired private CityPort cityPort;
    @Autowired private StreetPort streetPort;
    @Autowired private AddressPort addressPort;

    @Test
    void activeSubscriptionWithMatchingAddressFound() {
        Address address = saveAddress("Омская область-" + UUID.randomUUID());
        User user = saveUser();
        Subscription active = saveSubscription(user, address, true);

        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(address.id()));

        assertTrue(result.stream().anyMatch(s -> s.id().equals(active.id())));
    }

    @Test
    void inactiveSubscriptionNotReturned() {
        Address address = saveAddress("Новосибирская область-" + UUID.randomUUID());
        User user = saveUser();
        Subscription inactive = saveSubscription(user, address, false);

        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(address.id()));

        assertTrue(result.stream().noneMatch(s -> s.id().equals(inactive.id())));
    }

    @Test
    void subscriptionWithDifferentAddressNotReturned() {
        Address addressA = saveAddress("Томская область-" + UUID.randomUUID());
        Address addressB = saveAddress("Кемеровская область-" + UUID.randomUUID());
        User user = saveUser();
        saveSubscription(user, addressA, true);

        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(addressB.id()));

        assertTrue(result.isEmpty());
    }

    @Test
    void multipleAddressesReturnSubscriptionsForThoseAddresses() {
        Address address1 = saveAddress("Иркутская область-" + UUID.randomUUID());
        Address address2 = saveAddress("Алтайский край-" + UUID.randomUUID());
        Address address3 = saveAddress("Красноярский край-" + UUID.randomUUID());
        User user = saveUser();
        Subscription s1 = saveSubscription(user, address1, true);
        Subscription s2 = saveSubscription(user, address2, true);
        saveSubscription(user, address3, true);

        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(address1.id(), address2.id()));

        Set<UUID> ids = Set.copyOf(result.stream().map(Subscription::id).toList());
        assertTrue(ids.contains(s1.id()));
        assertTrue(ids.contains(s2.id()));
        assertEquals(2, result.size());
    }

    @Test
    void oneSubscriptionDoesNotAppearMultipleTimes() {
        Address address = saveAddress("Республика Хакасия-" + UUID.randomUUID());
        User user = saveUser();
        Subscription s = saveSubscription(user, address, true);

        // Query with set containing same id once (Set dedup) and also verify distinct
        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(address.id()));

        long count = result.stream().filter(sub -> sub.id().equals(s.id())).count();
        assertEquals(1, count);
        assertEquals(1, result.size());
    }

    @Test
    void emptyAddressIdsReturnsEmpty() {
        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void mixActiveAndInactiveOnlyActiveReturned() {
        Address address = saveAddress("Республика Тыва-" + UUID.randomUUID());
        User user1 = saveUser("a-" + UUID.randomUUID() + "@example.com");
        User user2 = saveUser("b-" + UUID.randomUUID() + "@example.com");
        Subscription active = saveSubscription(user1, address, true);
        Subscription inactive = saveSubscription(user2, address, false);

        List<Subscription> result = searchPort.findActiveByAddressIds(Set.of(address.id()));

        assertTrue(result.stream().anyMatch(s -> s.id().equals(active.id())));
        assertTrue(result.stream().noneMatch(s -> s.id().equals(inactive.id())));
        assertEquals(1, result.size());
    }

    private Address saveAddress(String regionName) {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), regionName));
        City city = cityPort.save(City.of(UUID.randomUUID(), region, "Город-" + UUID.randomUUID().toString().substring(0, 8)));
        Street street = streetPort.save(Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина-" + UUID.randomUUID().toString().substring(0, 8)));
        return addressPort.save(Address.of(UUID.randomUUID(), street, new House("15", null, "15")));
    }

    private User saveUser() {
        return saveUser(UUID.randomUUID() + "@example.com");
    }

    private User saveUser(String email) {
        return userPort.save(User.of(UUID.randomUUID(), email, UserRole.USER, true));
    }

    private Subscription saveSubscription(User user, Address address, boolean isActive) {
        Instant start = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant serviceUntil = Instant.now().plus(365, ChronoUnit.DAYS);
        return subscriptionPort.save(Subscription.of(UUID.randomUUID(), user, address, start, end, isActive, serviceUntil));
    }
}
