package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class PowerOutageMapperTest {

    @Autowired
    private PowerOutageMapper mapper;

    @Test
    void mapsDomainToEntity() {
        PowerOutage powerOutage = newPowerOutage();

        PowerOutageEntity entity = mapper.toEntity(powerOutage);

        assertEquals(powerOutage.id(), entity.getId());
        assertEquals(powerOutage.source().id(), entity.getSource().getId());
        assertEquals(powerOutage.startTime(), entity.getStartTime());
        assertEquals(powerOutage.endTime(), entity.getEndTime());
        assertEquals(powerOutage.reason(), entity.getReason());
        assertEquals(powerOutage.status(), entity.getStatus());
    }

    @Test
    void mapsAddressToEntityAndBackToDomainWithoutOwner() {
        PowerOutage powerOutage = newPowerOutage();
        PowerOutageAddress address = powerOutage.addresses().iterator().next();

        PowerOutageAddressEntity entity = mapper.toAddressEntity(address);

        assertEquals(address.id(), entity.getId());
        assertEquals(address.address().id(), entity.getAddress().getId());
        assertNull(entity.getPowerOutage());
        if (address.transformerStation() != null) {
            assertEquals(address.transformerStation().id(), entity.getTransformerStation().getId());
        }

        PowerOutageAddress restored = mapper.toDomain(entity);

        assertEquals(address.id(), restored.id());
        assertEquals(address.address().id(), restored.address().id());
        assertNull(restored.powerOutage());
        if (address.transformerStation() != null) {
            assertEquals(address.transformerStation().id(), restored.transformerStation().id());
        }
    }

    @Test
    void mapsEntityToDomainWithAddresses() {
        PowerOutage powerOutage = newPowerOutage();

        PowerOutageEntity entity = mapper.toEntity(powerOutage);

        PowerOutage restored = mapper.toDomain(entity, powerOutage.addresses());

        assertEquals(powerOutage.id(), restored.id());
        assertEquals(powerOutage.source().id(), restored.source().id());
        assertEquals(powerOutage.startTime(), restored.startTime());
        assertEquals(powerOutage.endTime(), restored.endTime());
        assertEquals(powerOutage.reason(), restored.reason());
        assertEquals(powerOutage.status(), restored.status());
        assertEquals(2, restored.addresses().size());
        for (PowerOutageAddress restoredAddress : restored.addresses()) {
            assertSame(restored, restoredAddress.powerOutage());
        }
    }

    @Test
    void mapsAddressWithTransformerStation() {
        PowerOutage powerOutage = newPowerOutage();
        PowerOutageAddress withStation = powerOutage.addresses().stream()
                .filter(a -> a.transformerStation() != null)
                .findFirst()
                .orElseThrow();

        PowerOutageAddressEntity entity = mapper.toAddressEntity(withStation);
        PowerOutageAddress restored = mapper.toDomain(entity);

        assertEquals(withStation.transformerStation().id(), restored.transformerStation().id());
    }

    @Test
    void mapsAddressWithoutTransformerStation() {
        PowerOutage powerOutage = newPowerOutage();
        PowerOutageAddress withoutStation = powerOutage.addresses().stream()
                .filter(a -> a.transformerStation() == null)
                .findFirst()
                .orElseThrow();

        PowerOutageAddressEntity entity = mapper.toAddressEntity(withoutStation);
        PowerOutageAddress restored = mapper.toDomain(entity);

        assertNull(restored.transformerStation());
        assertNull(entity.getTransformerStation());
    }

    private PowerOutage newPowerOutage() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);
        Address addressA = newAddress();
        Address addressB = newAddress();
        TransformerStation station = TransformerStation.of(UUID.randomUUID(), "ТП-101");
        PowerOutageAddress poaA = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressA);
        PowerOutageAddress poaB = PowerOutageAddress.unboundOf(UUID.randomUUID(), addressB, station);
        return PowerOutage.of(UUID.randomUUID(), source, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T05:00:00Z"), "Аварийное отключение", "АКТИВНО",
                List.of(poaA, poaB));
    }

    private Address newAddress() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        return Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }
}