package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuplicateResolverTest {

    @Mock private PowerOutagePort powerOutagePort;
    private DuplicateResolver resolver;
    private Source sourceA;
    private Source sourceB;
    private Address address1;
    private Address address2;
    private Instant start;

    @BeforeEach
    void setUp() {
        resolver = new DuplicateResolver(powerOutagePort);
        sourceA = Source.of(UUID.randomUUID(), "srcA", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        sourceB = Source.of(UUID.randomUUID(), "srcB", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street1 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Street street2 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "МИРА");
        address1 = Address.of(UUID.randomUUID(), street1, new House("15", null, "15"));
        address2 = Address.of(UUID.randomUUID(), street2, new House("10", null, "10"));
        start = Instant.parse("2026-01-01T00:00:00Z");
    }

    private PowerOutage existingOutage(Source source, String extRef) {
        return PowerOutage.of(UUID.randomUUID(), source, start, start.plusSeconds(3600), "r", "АКТИВНО", List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
    }

    @Test
    void externalReferenceSameSourceSameRefIsSameIdentity() {
        ParsedOutage po1 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        when(powerOutagePort.findBySourceAndExternalReference(sourceA.id(), "ext-123")).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithExternalReference(any(), eq("ext-123"))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, "ext-123")));
        resolver.resolve(po1, List.of(address1));
        verify(powerOutagePort).findBySourceAndExternalReference(sourceA.id(), "ext-123");
    }

    @Test
    void externalReferenceDifferentSourceIsDifferentIdentity() {
        ParsedOutage po1 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(sourceB, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        when(powerOutagePort.findBySourceAndExternalReference(sourceA.id(), "ext-123")).thenReturn(Optional.empty());
        when(powerOutagePort.findBySourceAndExternalReference(sourceB.id(), "ext-123")).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithExternalReference(any(), eq("ext-123"))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, "ext-123"))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceB, "ext-123")));
        resolver.resolve(po1, List.of(address1));
        resolver.resolve(po2, List.of(address1));
        verify(powerOutagePort).findBySourceAndExternalReference(sourceA.id(), "ext-123");
        verify(powerOutagePort).findBySourceAndExternalReference(sourceB.id(), "ext-123");
    }

    @Test
    void fallbackSameSourceStartAndAddressesIsSameIdentity() {
        Set<UUID> ids = Set.of(address1.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), eq(ids))).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null)));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po, List.of(address1));
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids);
    }

    @Test
    void fallbackDifferentSourceIsDifferent() {
        Set<UUID> ids = Set.of(address1.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), any(), any())).thenReturn(Optional.empty());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceB.id()), any(), any())).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceB, null)));
        ParsedOutage po1 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(sourceB, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po1, List.of(address1));
        resolver.resolve(po2, List.of(address1));
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids);
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceB.id(), start, ids);
    }

    @Test
    void fallbackDifferentStartTimeIsDifferent() {
        Instant start2 = start.plusSeconds(3600);
        Set<UUID> ids = Set.of(address1.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), any())).thenReturn(Optional.empty());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start2), any())).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null)));
        ParsedOutage po1 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(sourceA, start2, start2.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po1, List.of(address1));
        resolver.resolve(po2, List.of(address1));
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids);
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start2, ids);
    }

    @Test
    void fallbackDifferentAddressesIsDifferent() {
        Set<UUID> ids1 = Set.of(address1.id());
        Set<UUID> ids2 = Set.of(address2.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), eq(ids1))).thenReturn(Optional.empty());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), eq(ids2))).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null))).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null)));
        ParsedOutage po1 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po1, List.of(address1));
        resolver.resolve(po2, List.of(address2));
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids1);
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids2);
    }

    @Test
    void orderOfAddressesDoesNotMatter() {
        Set<UUID> ids = Set.of(address1.id(), address2.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), eq(ids))).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null))).thenReturn(new PowerOutagePort.CreateResult(false, existingOutage(sourceA, null)));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po, List.of(address1, address2));
        resolver.resolve(po, List.of(address2, address1));
        verify(powerOutagePort, org.mockito.Mockito.times(2)).findBySourceAndFallbackIdentity(sourceA.id(), start, ids);
    }

    @Test
    void duplicateAddressDoesNotChangeIdentity() {
        Set<UUID> ids = Set.of(address1.id());
        when(powerOutagePort.findBySourceAndFallbackIdentity(eq(sourceA.id()), eq(start), eq(ids))).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithFallback(any())).thenReturn(new PowerOutagePort.CreateResult(true, existingOutage(sourceA, null)));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        resolver.resolve(po, List.of(address1, address1));
        verify(powerOutagePort).findBySourceAndFallbackIdentity(sourceA.id(), start, ids);
    }

    @Test
    void endTimeDoesNotChangeIdentity() {
        Set<UUID> ids = Set.of(address1.id());
        Instant end1 = start.plusSeconds(3600);
        Instant end2 = start.plusSeconds(7200);
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), sourceA, start, end1, "r", "АКТИВНО", List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
        when(powerOutagePort.findBySourceAndFallbackIdentity(sourceA.id(), start, ids)).thenReturn(Optional.of(existing));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ParsedOutage po1 = new ParsedOutage(sourceA, start, end1, "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(sourceA, start, end2, "r", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var r1 = resolver.resolve(po1, List.of(address1));
        var r2 = resolver.resolve(po2, List.of(address1));
        assertEquals(DuplicateResolver.Decision.IGNORE, r1.decision());
        assertEquals(DuplicateResolver.Decision.UPDATE, r2.decision());
    }

    @Test
    void reasonDoesNotChangeIdentity() {
        Set<UUID> ids = Set.of(address1.id());
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), sourceA, start, start.plusSeconds(3600), "old", "АКТИВНО", List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
        when(powerOutagePort.findBySourceAndFallbackIdentity(sourceA.id(), start, ids)).thenReturn(Optional.of(existing));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ParsedOutage poSame = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "old", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage poDiff = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "new", null, List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var rSame = resolver.resolve(poSame, List.of(address1));
        var rDiff = resolver.resolve(poDiff, List.of(address1));
        assertEquals(DuplicateResolver.Decision.IGNORE, rSame.decision());
        assertEquals(DuplicateResolver.Decision.UPDATE, rDiff.decision());
    }

    @Test
    void resolutionCreateWhenAbsent() {
        when(powerOutagePort.findBySourceAndExternalReference(any(), any())).thenReturn(Optional.empty());
        PowerOutage created = existingOutage(sourceA, "ext-123");
        when(powerOutagePort.tryCreateWithExternalReference(any(), any())).thenReturn(new PowerOutagePort.CreateResult(true, created));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var result = resolver.resolve(po, List.of(address1));
        assertEquals(DuplicateResolver.Decision.CREATE, result.decision());
        verify(powerOutagePort).tryCreateWithExternalReference(any(), eq("ext-123"));
    }

    @Test
    void resolutionCreateWhenAbsentButConcurrentlyCreatedReturnsIgnore() {
        PowerOutage existing = existingOutage(sourceA, "ext-123");
        when(powerOutagePort.findBySourceAndExternalReference(any(), any())).thenReturn(Optional.empty());
        when(powerOutagePort.tryCreateWithExternalReference(any(), any())).thenReturn(new PowerOutagePort.CreateResult(false, existing));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var result = resolver.resolve(po, List.of(address1));
        // Should be IGNORE because existing data same (endTime/reason same as po)
        assertEquals(DuplicateResolver.Decision.IGNORE, result.decision());
    }

    @Test
    void resolutionUpdateWhenChanged() {
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), sourceA, start, start.plusSeconds(3600), "old", "АКТИВНО", List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
        when(powerOutagePort.findBySourceAndExternalReference(sourceA.id(), "ext-123")).thenReturn(Optional.of(existing));
        when(powerOutagePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(7200), "new", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var result = resolver.resolve(po, List.of(address1));
        assertEquals(DuplicateResolver.Decision.UPDATE, result.decision());
        verify(powerOutagePort).save(any());
    }

    @Test
    void resolutionIgnoreWhenUnchanged() {
        PowerOutage existing = PowerOutage.of(UUID.randomUUID(), sourceA, start, start.plusSeconds(3600), "r", "АКТИВНО", List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
        when(powerOutagePort.findBySourceAndExternalReference(sourceA.id(), "ext-123")).thenReturn(Optional.of(existing));
        ParsedOutage po = new ParsedOutage(sourceA, start, start.plusSeconds(3600), "r", "ext-123", List.of(new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        var result = resolver.resolve(po, List.of(address1));
        assertEquals(DuplicateResolver.Decision.IGNORE, result.decision());
        verify(powerOutagePort, never()).save(any());
        verify(powerOutagePort, never()).tryCreateWithExternalReference(any(), any());
    }
}
