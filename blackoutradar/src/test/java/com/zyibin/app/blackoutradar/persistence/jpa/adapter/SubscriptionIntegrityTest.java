package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SubscriptionTransformerStationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.UserEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.AddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.StreetJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SubscriptionTransformerStationJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.TransformerStationJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.EntityManager;
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
class SubscriptionIntegrityTest {

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private RegionJpaRepository regionRepository;

    @Autowired
    private CityJpaRepository cityRepository;

    @Autowired
    private StreetJpaRepository streetRepository;

    @Autowired
    private AddressJpaRepository addressRepository;

    @Autowired
    private TransformerStationJpaRepository stationRepository;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    @Autowired
    private SubscriptionTransformerStationJpaRepository stationAssociationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void validSubscriptionPersistsWithForeignKeys() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();

        SubscriptionEntity saved = subscriptionRepository.saveAndFlush(
                newSubscriptionEntity(user, address, validStart(), validEnd()));

        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals(address.getId(), saved.getAddress().getId());
    }

    @Test
    void subscriptionWithMissingUserIsRejected() {
        AddressEntity address = saveAddressGraph();
        UserEntity missingUser = newUserEntity();
        missingUser.setId(UUID.randomUUID());

        SubscriptionEntity invalid = newSubscriptionEntity(missingUser, address, validStart(), validEnd());

        assertThrows(DataIntegrityViolationException.class,
                () -> subscriptionRepository.saveAndFlush(invalid));
    }

    @Test
    void subscriptionWithMissingAddressIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity missingAddress = new AddressEntity();
        missingAddress.setId(UUID.randomUUID());

        SubscriptionEntity invalid = newSubscriptionEntity(user, missingAddress, validStart(), validEnd());

        assertThrows(DataIntegrityViolationException.class,
                () -> subscriptionRepository.saveAndFlush(invalid));
    }

    @Test
    void monitoringRangeViolationIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();

        SubscriptionEntity invalid = newSubscriptionEntity(user, address, validEnd(), validStart());

        assertThrows(DataIntegrityViolationException.class,
                () -> subscriptionRepository.saveAndFlush(invalid));
    }

    @Test
    void associationWithMissingStationIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();
        SubscriptionEntity subscription = subscriptionRepository.saveAndFlush(
                newSubscriptionEntity(user, address, validStart(), validEnd()));
        TransformerStationEntity missingStation = new TransformerStationEntity();
        missingStation.setId(UUID.randomUUID());
        missingStation.setName("ТП-999");

        SubscriptionTransformerStationEntity invalid =
                newAssociationEntity(subscription, missingStation);

        assertThrows(DataIntegrityViolationException.class,
                () -> stationAssociationRepository.saveAndFlush(invalid));
    }

    @Test
    void duplicateStationAssociationIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();
        SubscriptionEntity subscription = subscriptionRepository.saveAndFlush(
                newSubscriptionEntity(user, address, validStart(), validEnd()));
        TransformerStationEntity station = stationRepository.saveAndFlush(newStationEntity("ТП-101"));

        stationAssociationRepository.saveAndFlush(newAssociationEntity(subscription, station));

        assertThrows(DataIntegrityViolationException.class,
                () -> stationAssociationRepository.saveAndFlush(newAssociationEntity(subscription, station)));
    }

    @Test
    void deletingSubscriptionCascadesToAssociations() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();
        SubscriptionEntity subscription = subscriptionRepository.saveAndFlush(
                newSubscriptionEntity(user, address, validStart(), validEnd()));
        TransformerStationEntity station = stationRepository.saveAndFlush(newStationEntity("ТП-101"));
        stationAssociationRepository.saveAndFlush(newAssociationEntity(subscription, station));

        entityManager.clear();

        subscriptionRepository.deleteById(subscription.getId());
        subscriptionRepository.flush();

        assertTrue(stationAssociationRepository.findAllBySubscriptionId(subscription.getId()).isEmpty());
    }

    @Test
    void deletingUserReferencedBySubscriptionIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();
        subscriptionRepository.saveAndFlush(newSubscriptionEntity(user, address, validStart(), validEnd()));

        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.deleteById(user.getId());
            userRepository.flush();
        });
    }

    @Test
    void deletingStationReferencedByAssociationIsRejected() {
        UserEntity user = userRepository.saveAndFlush(newUserEntity());
        AddressEntity address = saveAddressGraph();
        SubscriptionEntity subscription = subscriptionRepository.saveAndFlush(
                newSubscriptionEntity(user, address, validStart(), validEnd()));
        TransformerStationEntity station = stationRepository.saveAndFlush(newStationEntity("ТП-101"));
        stationAssociationRepository.saveAndFlush(newAssociationEntity(subscription, station));

        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            stationRepository.deleteById(station.getId());
            stationRepository.flush();
        });
    }

    private AddressEntity saveAddressGraph() {
        RegionEntity region = regionRepository.saveAndFlush(newRegionEntity());
        CityEntity city = cityRepository.saveAndFlush(newCityEntity(region));
        StreetEntity street = streetRepository.saveAndFlush(newStreetEntity(city));
        return addressRepository.saveAndFlush(newAddressEntity(city, street));
    }

    private UserEntity newUserEntity() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmail("ivan@example.com");
        entity.setRole(UserRole.USER);
        entity.setActive(true);
        return entity;
    }

    private RegionEntity newRegionEntity() {
        RegionEntity entity = new RegionEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Омская область");
        return entity;
    }

    private CityEntity newCityEntity(RegionEntity region) {
        CityEntity entity = new CityEntity();
        entity.setId(UUID.randomUUID());
        entity.setRegion(region);
        entity.setName("Омск");
        return entity;
    }

    private StreetEntity newStreetEntity(CityEntity city) {
        StreetEntity entity = new StreetEntity();
        entity.setId(UUID.randomUUID());
        entity.setCity(city);
        entity.setType(StreetType.STREET);
        entity.setCanonicalName("Ленина");
        return entity;
    }

    private AddressEntity newAddressEntity(CityEntity city, StreetEntity street) {
        AddressEntity entity = new AddressEntity();
        entity.setId(UUID.randomUUID());
        entity.setCity(city);
        entity.setStreet(street);
        entity.setHouseNumber("15");
        entity.setCanonicalHouse("15");
        return entity;
    }

    private TransformerStationEntity newStationEntity(String name) {
        TransformerStationEntity entity = new TransformerStationEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        return entity;
    }

    private SubscriptionEntity newSubscriptionEntity(UserEntity user, AddressEntity address,
                                                     Instant monitoringStart, Instant monitoringEnd) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUser(user);
        entity.setAddress(address);
        entity.setMonitoringStart(monitoringStart);
        entity.setMonitoringEnd(monitoringEnd);
        entity.setActive(true);
        entity.setServiceAccessUntil(Instant.now().plus(365, ChronoUnit.DAYS));
        return entity;
    }

    private SubscriptionTransformerStationEntity newAssociationEntity(SubscriptionEntity subscription,
                                                                      TransformerStationEntity station) {
        SubscriptionTransformerStationEntity entity = new SubscriptionTransformerStationEntity();
        entity.setId(UUID.randomUUID());
        entity.setSubscription(subscription);
        entity.setTransformerStation(station);
        return entity;
    }

    private Instant validStart() {
        return Instant.now();
    }

    private Instant validEnd() {
        return Instant.now().plus(30, ChronoUnit.DAYS);
    }
}