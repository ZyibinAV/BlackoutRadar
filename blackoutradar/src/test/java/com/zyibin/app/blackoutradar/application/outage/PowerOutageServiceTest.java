package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PowerOutageServiceTest {

    @Mock private PowerOutagePort powerOutagePort;
    @Mock private SourcePort sourcePort;

    private PowerOutageService service;

    private Source source;
    private Address address;

    @BeforeEach
    void setUp() {
        service = new PowerOutageService(powerOutagePort, sourcePort);
        source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }

    private PowerOutageAddress poa(Address addr) {
        return PowerOutageAddress.unboundOf(UUID.randomUUID(), addr);
    }

    @Test
    void createResolvesSourceAndSaves() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");
        PowerOutageAddress a = poa(address);
        when(sourcePort.findById(source.id())).thenReturn(Optional.of(source));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PowerOutage result = service.create(source.id(), start, end, "Аварийное", "АКТИВНО", Set.of(a));

        assertEquals(source.id(), result.source().id());
        assertEquals(start, result.startTime());
        assertEquals("Аварийное", result.reason());
        verify(sourcePort).findById(source.id());
        verify(powerOutagePort).save(any());
        // capture saved
        ArgumentCaptor<PowerOutage> captor = ArgumentCaptor.forClass(PowerOutage.class);
        verify(powerOutagePort).save(captor.capture());
        assertEquals(source, captor.getValue().source());
    }

    @Test
    void createFailsWhenSourceAbsentAndDoesNotSave() {
        when(sourcePort.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () ->
                service.create(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600), "r", "s", Set.of(poa(address))));
        verify(sourcePort).findById(any());
        verify(powerOutagePort, never()).save(any());
    }

    @Test
    void getByIdReturnsExisting() {
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное", "АКТИВНО", Set.of(poa(address)));
        when(powerOutagePort.findById(outage.id())).thenReturn(Optional.of(outage));
        PowerOutage result = service.getById(outage.id());
        assertEquals(outage.id(), result.id());
        verify(powerOutagePort).findById(outage.id());
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(powerOutagePort.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getById(UUID.randomUUID()));
    }

    @Test
    void findByIdDelegates() {
        UUID id = UUID.randomUUID();
        when(powerOutagePort.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.findById(id).isEmpty());
        verify(powerOutagePort).findById(id);
    }

    @Test
    void updateUsesExistingSourceAndCreatesNewInstance() {
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное", "АКТИВНО", Set.of(poa(address)));
        when(powerOutagePort.findById(existing.id())).thenReturn(Optional.of(existing));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant newStart = Instant.parse("2026-01-02T00:00:00Z");
        Instant newEnd = Instant.parse("2026-01-02T05:00:00Z");
        Address address2 = Address.of(UUID.randomUUID(),
                Street.of(UUID.randomUUID(), City.of(UUID.randomUUID(), Region.of(UUID.randomUUID(), "Новосибирская область"), "Новосибирск"), StreetType.STREET, "Мира"),
                new House("10", null, "10"));
        PowerOutageAddress newPoa = poa(address2);

        PowerOutage result = service.update(existing.id(), newStart, newEnd, "Плановое", "ЗАВЕРШЕНО", Set.of(newPoa));

        assertEquals(existing.id(), result.id());
        assertEquals(existing.source(), result.source());
        assertEquals(newStart, result.startTime());
        assertEquals("Плановое", result.reason());
        assertEquals("ЗАВЕРШЕНО", result.status());
        // ensure immutable: original unchanged
        assertEquals("Аварийное", existing.reason());
        verify(powerOutagePort).findById(existing.id());
        verify(powerOutagePort).save(any());
        verify(sourcePort, never()).findById(any());
    }

    @Test
    void updateFailsWhenOutageAbsent() {
        when(powerOutagePort.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () ->
                service.update(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600), "r", "s", Set.of(poa(address))));
        verify(powerOutagePort).findById(any());
        verify(powerOutagePort, never()).save(any());
    }

    @Test
    void updatePreservesDomainInvariants() {
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное", "АКТИВНО", Set.of(poa(address)));
        when(powerOutagePort.findById(existing.id())).thenReturn(Optional.of(existing));

        // start after end should be rejected by domain
        assertThrows(IllegalArgumentException.class, () ->
                service.update(existing.id(), Instant.parse("2026-01-02T05:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), "r", "s", Set.of(poa(address))));
    }

    @Test
    void noDirectPersistenceAdapterUsage() {
        // Application should not use JPA/Repository - verified by imports inspection, but here verify only ports used
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное", "АКТИВНО", Set.of(poa(address)));
        when(powerOutagePort.findById(any())).thenReturn(Optional.of(existing));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(existing.id(), Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-02T05:00:00Z"), "r", "s", Set.of(poa(address)));
        verifyNoMoreInteractions(sourcePort);
    }
}
