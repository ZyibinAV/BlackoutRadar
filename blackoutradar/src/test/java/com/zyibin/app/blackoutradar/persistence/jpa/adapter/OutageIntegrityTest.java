package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RegionEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.AddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.CityJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageAddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.SourceJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.StreetJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.TransformerStationJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class OutageIntegrityTest {

    @Autowired
    private SourceJpaRepository sourceRepository;

    @Autowired
    private PowerOutageJpaRepository powerOutageRepository;

    @Autowired
    private PowerOutageAddressJpaRepository powerOutageAddressRepository;

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
    private EntityManager entityManager;

    @Test
    void sourceWithoutConfigurationIsAccepted() {
        SourceEntity saved = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));

        assertNull(saved.getConfiguration());
    }

    @Test
    void duplicateSourceNameIsRejected() {
        sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", "{\"channel\": \"telegram\"}"));

        assertThrows(DataIntegrityViolationException.class,
                () -> sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null)));
    }

    @Test
    void powerOutageWithMissingSourceIsRejected() {
        SourceEntity missingSource = newSourceEntity("Несуществующий", "{}");
        missingSource.setId(UUID.randomUUID());

        PowerOutageEntity invalid = newPowerOutageEntity(missingSource, validStart(), validEnd());

        assertThrows(DataIntegrityViolationException.class,
                () -> powerOutageRepository.saveAndFlush(invalid));
    }

    @Test
    void powerOutageTimeRangeViolationIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));

        PowerOutageEntity invalid = newPowerOutageEntity(source, validEnd(), validStart());

        assertThrows(DataIntegrityViolationException.class,
                () -> powerOutageRepository.saveAndFlush(invalid));
    }

    @Test
    void powerOutageAddressWithMissingAddressIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        PowerOutageEntity powerOutage = powerOutageRepository.saveAndFlush(
                newPowerOutageEntity(source, validStart(), validEnd()));
        AddressEntity missingAddress = new AddressEntity();
        missingAddress.setId(UUID.randomUUID());

        PowerOutageAddressEntity invalid = newPoaEntity(powerOutage, missingAddress, null);

        assertThrows(DataIntegrityViolationException.class,
                () -> powerOutageAddressRepository.saveAndFlush(invalid));
    }

    @Test
    void duplicateAddressInPowerOutageIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        PowerOutageEntity powerOutage = powerOutageRepository.saveAndFlush(
                newPowerOutageEntity(source, validStart(), validEnd()));
        AddressEntity address = saveAddressGraph();

        powerOutageAddressRepository.saveAndFlush(newPoaEntity(powerOutage, address, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> powerOutageAddressRepository.saveAndFlush(newPoaEntity(powerOutage, address, null)));
    }

    @Test
    void deletingPowerOutageCascadesToAddresses() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        PowerOutageEntity powerOutage = powerOutageRepository.saveAndFlush(
                newPowerOutageEntity(source, validStart(), validEnd()));
        AddressEntity address = saveAddressGraph();
        powerOutageAddressRepository.saveAndFlush(newPoaEntity(powerOutage, address, null));

        entityManager.clear();

        powerOutageRepository.deleteById(powerOutage.getId());
        powerOutageRepository.flush();

        assertTrue(powerOutageAddressRepository.findAllByPowerOutageId(powerOutage.getId()).isEmpty());
    }

    @Test
    void deletingSourceReferencedByPowerOutageIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        powerOutageRepository.saveAndFlush(newPowerOutageEntity(source, validStart(), validEnd()));

        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            sourceRepository.deleteById(source.getId());
            sourceRepository.flush();
        });
    }

    @Test
    void deletingAddressReferencedByPowerOutageAddressIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        PowerOutageEntity powerOutage = powerOutageRepository.saveAndFlush(
                newPowerOutageEntity(source, validStart(), validEnd()));
        AddressEntity address = saveAddressGraph();
        powerOutageAddressRepository.saveAndFlush(newPoaEntity(powerOutage, address, null));

        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            addressRepository.deleteById(address.getId());
            addressRepository.flush();
        });
    }

    @Test
    void deletingTransformerStationReferencedByPowerOutageAddressIsRejected() {
        SourceEntity source = sourceRepository.saveAndFlush(newSourceEntity("Горэлектросеть", null));
        PowerOutageEntity powerOutage = powerOutageRepository.saveAndFlush(
                newPowerOutageEntity(source, validStart(), validEnd()));
        AddressEntity address = saveAddressGraph();
        TransformerStationEntity station = stationRepository.saveAndFlush(newStationEntity("ТП-101"));
        powerOutageAddressRepository.saveAndFlush(newPoaEntity(powerOutage, address, station));

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

    private SourceEntity newSourceEntity(String name, String configuration) {
        SourceEntity entity = new SourceEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setSourceType("ТЕЛЕГРАМ");
        entity.setProviderType("Официальный");
        entity.setConfiguration(configuration);
        entity.setSchedule("0 6 * * *");
        entity.setActive(true);
        return entity;
    }

    private PowerOutageEntity newPowerOutageEntity(SourceEntity source, Instant startTime, Instant endTime) {
        PowerOutageEntity entity = new PowerOutageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSource(source);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setReason("Аварийное отключение");
        entity.setStatus("АКТИВНО");
        return entity;
    }

    private PowerOutageAddressEntity newPoaEntity(PowerOutageEntity powerOutage, AddressEntity address,
                                                  TransformerStationEntity station) {
        PowerOutageAddressEntity entity = new PowerOutageAddressEntity();
        entity.setId(UUID.randomUUID());
        entity.setPowerOutage(powerOutage);
        entity.setAddress(address);
        entity.setTransformerStation(station);
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

    private Instant validStart() {
        return Instant.parse("2026-01-01T00:00:00Z");
    }

    private Instant validEnd() {
        return Instant.parse("2026-01-01T05:00:00Z");
    }
}