package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.application.address.AddressService;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParsedOutageProcessorUnitTest {

    @Mock AddressService addressService;
    @InjectMocks ParsedOutageProcessor processor;

    @Test
    void resolvesMultipleAddressesViaService() {
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        AddressInput in1 = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        AddressInput in2 = new AddressInput("Омская область", null, null, "Омск", null, "ул Мира", "10");
        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street s1 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Street s2 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "МИРА");
        Address a1 = Address.of(UUID.randomUUID(), s1, new House("15", null, "15"));
        Address a2 = Address.of(UUID.randomUUID(), s2, new House("10", null, "10"));
        when(addressService.resolve(in1)).thenReturn(a1);
        when(addressService.resolve(in2)).thenReturn(a2);

        ParsedOutage po = new ParsedOutage(source.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", "ext-1", List.of(in1, in2));
        List<Address> result = processor.resolveAddresses(po);

        assertEquals(2, result.size());
        assertEquals(a1, result.get(0));
        assertEquals(a2, result.get(1));
        verify(addressService).resolve(in1);
        verify(addressService).resolve(in2);
        verifyNoMoreInteractions(addressService);
    }

    @Test
    void preservesExternalReferenceNotUsedForResolution() {
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        AddressInput in = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Address addr = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        when(addressService.resolve(in)).thenReturn(addr);

        ParsedOutage withRef = new ParsedOutage(source.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", "ext-123", List.of(in));
        ParsedOutage withoutRef = new ParsedOutage(source.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(in));

        assertNotNull(withRef.externalReference());
        assertEquals("ext-123", withRef.externalReference());
        assertEquals(1, processor.resolveAddresses(withRef).size());
        assertEquals(1, processor.resolveAddresses(withoutRef).size());
    }
}
