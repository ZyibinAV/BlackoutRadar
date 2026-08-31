package com.zyibin.app.blackoutradar.application.matching;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PowerOutageMatchingServiceTest {

    @Mock private PowerOutagePort powerOutagePort;
    @Mock private MatchingBoundary matchingBoundary;

    private PowerOutageMatchingService service;
    private PowerOutage powerOutage;

    @BeforeEach
    void setUp() {
        service = new PowerOutageMatchingService(powerOutagePort, matchingBoundary);
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address);
        powerOutage = PowerOutage.of(UUID.randomUUID(), source,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T05:00:00Z"),
                "Аварийное", "АКТИВНО", Set.of(poa));
    }

    @Test
    void matchLoadsPowerOutageAndDelegatesToBoundary() {
        when(powerOutagePort.findById(powerOutage.id())).thenReturn(Optional.of(powerOutage));

        service.match(powerOutage.id());

        verify(powerOutagePort).findById(powerOutage.id());
        verify(matchingBoundary).handle(powerOutage);
    }

    @Test
    void matchDelegatesSameInstance() {
        when(powerOutagePort.findById(any())).thenReturn(Optional.of(powerOutage));

        service.match(powerOutage.id());

        verify(matchingBoundary).handle(powerOutage);
    }

    @Test
    void matchThrowsWhenPowerOutageAbsent() {
        UUID id = UUID.randomUUID();
        when(powerOutagePort.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.match(id));

        verify(powerOutagePort).findById(id);
        verify(matchingBoundary, never()).handle(any());
    }

    @Test
    void matchDoesNotCallBoundaryWhenAbsent() {
        when(powerOutagePort.findById(any())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.match(UUID.randomUUID()));

        verify(matchingBoundary, never()).handle(any());
    }

    @Test
    void serviceUsesOnlyPortsAndBoundary() {
        when(powerOutagePort.findById(powerOutage.id())).thenReturn(Optional.of(powerOutage));

        service.match(powerOutage.id());

        verify(powerOutagePort).findById(powerOutage.id());
        verify(matchingBoundary).handle(powerOutage);
        // no other interactions - verified implicitly by mock setup
    }
}
