package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class SubscriptionMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private TransformerStationMapper transformerStationMapper;

    @Autowired
    private SubscriptionMapper mapper;

    @Test
    void mapsDomainToEntityWithUserAndAddress() {
        Subscription subscription = newSubscription(Set.of());

        SubscriptionEntity entity = mapper.toEntity(subscription);

        assertEquals(subscription.id(), entity.getId());
        assertEquals(subscription.user().id(), entity.getUser().getId());
        assertEquals(subscription.address().id(), entity.getAddress().getId());
        assertEquals(subscription.monitoringStart(), entity.getMonitoringStart());
        assertEquals(subscription.monitoringEnd(), entity.getMonitoringEnd());
        assertTrue(entity.isActive());
        assertEquals(subscription.serviceAccessUntil(), entity.getServiceAccessUntil());
    }

    @Test
    void mapsEntityToDomainWithoutStations() {
        Subscription subscription = newSubscription(Set.of());

        SubscriptionEntity entity = mapper.toEntity(subscription);

        Subscription restored = mapper.toDomain(entity, new LinkedHashSet<>());

        assertEquals(subscription.id(), restored.id());
        assertEquals(subscription.user().id(), restored.user().id());
        assertEquals(subscription.address().id(), restored.address().id());
        assertEquals(subscription.monitoringStart(), restored.monitoringStart());
        assertEquals(subscription.monitoringEnd(), restored.monitoringEnd());
        assertEquals(subscription.isActive(), restored.isActive());
        assertEquals(subscription.serviceAccessUntil(), restored.serviceAccessUntil());
        assertTrue(restored.transformerStations().isEmpty());
    }

    @Test
    void mapsEntityToDomainWithStations() {
        TransformerStation stationA = TransformerStation.of(UUID.randomUUID(), "ТП-101");
        TransformerStation stationB = TransformerStation.of(UUID.randomUUID(), "ТП-102");
        Subscription subscription = newSubscription(Set.of(stationA, stationB));

        SubscriptionEntity entity = mapper.toEntity(subscription);

        Subscription restored = mapper.toDomain(entity, new LinkedHashSet<>(Set.of(stationA, stationB)));

        assertEquals(2, restored.transformerStations().size());
        assertEquals(Set.of(stationA.id(), stationB.id()),
                Set.copyOf(restored.transformerStations().stream().map(TransformerStation::id).toList()));
    }

    @Test
    void roundTripPreservesIdentityAndBaseFields() {
        TransformerStation station = TransformerStation.of(UUID.randomUUID(), "ТП-101");
        Subscription original = newSubscription(Set.of(station));

        SubscriptionEntity entity = mapper.toEntity(original);
        Subscription restored = mapper.toDomain(entity, new LinkedHashSet<>(Set.of(station)));

        assertEquals(original.id(), restored.id());
        assertEquals(original.user().id(), restored.user().id());
        assertEquals(original.address().id(), restored.address().id());
        assertEquals(original.monitoringStart(), restored.monitoringStart());
        assertEquals(original.monitoringEnd(), restored.monitoringEnd());
        assertEquals(original.isActive(), restored.isActive());
        assertEquals(original.serviceAccessUntil(), restored.serviceAccessUntil());
        assertEquals(Set.of(station.id()), Set.copyOf(restored.transformerStations().stream()
                .map(TransformerStation::id).toList()));
    }

    @Test
    void toEntityKeepsAddressWithoutCityDistrict() {
        Subscription subscription = newSubscription(Set.of());

        SubscriptionEntity entity = mapper.toEntity(subscription);

        assertNull(entity.getAddress().getCityDistrict());
        assertEquals(subscription.address().street().id(), entity.getAddress().getStreet().getId());
    }

    private Subscription newSubscription(Set<TransformerStation> stations) {
        User user = User.of(UUID.randomUUID(), "ivan@example.com", UserRole.USER, true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        Instant start = Instant.now();
        return Subscription.of(UUID.randomUUID(), user, address, start,
                start.plus(30, ChronoUnit.DAYS), true, start.plus(365, ChronoUnit.DAYS), stations);
    }
}