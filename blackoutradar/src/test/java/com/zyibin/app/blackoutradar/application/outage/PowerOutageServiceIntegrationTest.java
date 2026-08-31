package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.application.address.AddressService;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import java.time.Instant;
import java.util.Optional;
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
class PowerOutageServiceIntegrationTest {

    @Autowired private PowerOutageService powerOutageService;
    @Autowired private SourcePort sourcePort;
    @Autowired private AddressService addressService;

    @Test
    void createAndGetViaApplicationBoundary() {
        Source source = sourcePort.save(Source.of(UUID.randomUUID(), "src-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        Address address = addressService.resolve(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");
        PowerOutage created = powerOutageService.create(source.id(), start, end, "Аварийное", "АКТИВНО", Set.of(poa));

        assertEquals(source.id(), created.source().id());
        assertEquals(start, created.startTime());

        Optional<PowerOutage> found = powerOutageService.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals(created.id(), found.get().id());

        PowerOutage got = powerOutageService.getById(created.id());
        assertEquals(created.id(), got.id());
    }

    @Test
    void updateViaApplicationBoundary() {
        Source source = sourcePort.save(Source.of(UUID.randomUUID(), "src-" + UUID.randomUUID(), "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        Address address = addressService.resolve(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15"));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");
        PowerOutage created = powerOutageService.create(source.id(), start, end, "Аварийное", "АКТИВНО", Set.of(poa));

        Instant newStart = Instant.parse("2026-01-02T00:00:00Z");
        Instant newEnd = Instant.parse("2026-01-02T05:00:00Z");
        PowerOutageAddress newPoa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        PowerOutage updated = powerOutageService.update(created.id(), newStart, newEnd, "Плановое", "ЗАВЕРШЕНО", Set.of(newPoa));

        assertEquals(created.id(), updated.id());
        assertEquals(created.source().id(), updated.source().id());
        assertEquals(newStart, updated.startTime());
        assertEquals("Плановое", updated.reason());
    }
}
