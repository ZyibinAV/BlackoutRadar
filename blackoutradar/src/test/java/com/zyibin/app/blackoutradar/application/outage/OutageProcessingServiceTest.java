package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutageProcessingServiceTest {

    @Mock private ParsedOutageProcessor parsedOutageProcessor;
    @Mock private DuplicateResolver duplicateResolver;

    private OutageProcessingService service;

    private Source source;
    private ParsedOutage parsedOutage;
    private Address address1;
    private Address address2;
    private PowerOutage powerOutage;

    @BeforeEach
    void setUp() {
        service = new OutageProcessingService(parsedOutageProcessor, duplicateResolver);

        source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        AddressInput input = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        parsedOutage = new ParsedOutage(source, start, end, "reason", "ext-1", List.of(input));

        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street1 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Street street2 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "МИРА");
        address1 = Address.of(UUID.randomUUID(), street1, new House("15", null, "15"));
        address2 = Address.of(UUID.randomUUID(), street2, new House("10", null, "10"));

        powerOutage = PowerOutage.of(UUID.randomUUID(), source, start, end, "reason", "АКТИВНО",
                List.of(com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
    }

    @Test
    void processDelegatesToProcessorAndResolverAndReturnsCreate() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult expected = new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.CREATE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(expected);

        DuplicateResolver.ResolutionResult result = service.process(parsedOutage);

        assertSame(expected, result);
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void processReturnsUpdateResult() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult expected = new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.UPDATE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(expected);

        DuplicateResolver.ResolutionResult result = service.process(parsedOutage);

        assertEquals(DuplicateResolver.Decision.UPDATE, result.decision());
        assertSame(powerOutage, result.powerOutage());
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void processReturnsIgnoreResult() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult expected = new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.IGNORE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(expected);

        DuplicateResolver.ResolutionResult result = service.process(parsedOutage);

        assertEquals(DuplicateResolver.Decision.IGNORE, result.decision());
        assertSame(powerOutage, result.powerOutage());
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void processPassesCanonicalAddressesFromProcessorToResolver() {
        List<Address> canonical = List.of(address1, address2);
        DuplicateResolver.ResolutionResult expected = new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.CREATE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(expected);

        service.process(parsedOutage);

        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void whenProcessorThrowsResolverNotCalled() {
        RuntimeException ex = new RuntimeException("processor failure");
        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.process(parsedOutage));

        assertSame(ex, thrown);
        verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        verifyNoInteractions(duplicateResolver);
        verifyNoMoreInteractions(parsedOutageProcessor);
    }

    @Test
    void whenResolverThrowsExceptionPropagatedNotHidden() {
        List<Address> canonical = List.of(address1);
        RuntimeException ex = new IllegalStateException("resolver failure");

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.process(parsedOutage));

        assertSame(ex, thrown);
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void processRequiresNonNullParsedOutage() {
        assertThrows(NullPointerException.class, () -> service.process(null));
        verifyNoInteractions(parsedOutageProcessor, duplicateResolver);
    }
}
