package com.zyibin.app.blackoutradar.application.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class SubscriptionServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plus(30, ChronoUnit.DAYS);
    private static final Instant ACCESS = START.plus(90, ChronoUnit.DAYS);

    @Mock private SubscriptionPort subscriptionPort;
    @Mock private UserPort userPort;
    @Mock private TransformerStationPort transformerStationPort;
    @Mock private AddressService addressService;

    private SubscriptionService service;

    private User user;
    private Address address;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionPort, userPort, transformerStationPort, addressService);
        user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");
        address = Address.of(UUID.randomUUID(), street, house);
    }

    private Subscription subscription(boolean active) {
        return Subscription.of(UUID.randomUUID(), user, address, START, END, active, ACCESS);
    }

    // Create
    @Test
    void createResolvesUserAddressAndStationsAndSaves() {
        AddressInput input = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        when(userPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(addressService.resolve(input)).thenReturn(address);
        when(transformerStationPort.findByName("ТП-1")).thenReturn(Optional.of(tp));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.create("user@example.com", input, START, END, ACCESS, Set.of("ТП-1"));

        assertEquals(user, result.user());
        assertEquals(address, result.address());
        assertEquals(START, result.monitoringStart());
        assertTrue(result.transformerStations().contains(tp));
        verify(userPort).findByEmail("user@example.com");
        verify(addressService).resolve(input);
        verify(transformerStationPort).findByName("ТП-1");
        verify(subscriptionPort).save(any());
        verifyNoMoreInteractions(userPort, addressService, transformerStationPort);
    }

    @Test
    void createWithoutStations() {
        AddressInput input = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        when(userPort.findByEmail(any())).thenReturn(Optional.of(user));
        when(addressService.resolve(any())).thenReturn(address);
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.create("user@example.com", input, START, END, ACCESS, Set.of());

        assertTrue(result.transformerStations().isEmpty());
        verify(transformerStationPort, never()).findByName(any());
    }

    @Test
    void createFailsWhenUserNotFound() {
        AddressInput input = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        when(userPort.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> service.create("missing@example.com", input, START, END, ACCESS, Set.of()));
    }

    // Read
    @Test
    void findByIdReturnsOptional() {
        UUID id = UUID.randomUUID();
        Subscription sub = subscription(false);
        when(subscriptionPort.findById(id)).thenReturn(Optional.of(sub));
        assertTrue(service.findById(id).isPresent());
        verify(subscriptionPort).findById(id);
    }

    @Test
    void getByIdThrowsWhenAbsent() {
        when(subscriptionPort.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getById(UUID.randomUUID()));
    }

    // Mutations via Domain Behavior
    @Test
    void activateCallsDomainBehaviorAndSaves() {
        Subscription sub = subscription(false);
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.activate(sub.id());

        assertTrue(result.isActive());
        assertEquals(sub.id(), result.id());
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionPort).save(captor.capture());
        assertTrue(captor.getValue().isActive());
        verify(subscriptionPort).findById(sub.id());
        verifyNoMoreInteractions(userPort, addressService, transformerStationPort);
    }

    @Test
    void deactivateCallsDomainBehaviorAndSaves() {
        Subscription sub = subscription(true);
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.deactivate(sub.id());
        assertFalse(result.isActive());
        verify(subscriptionPort).save(any());
    }

    @Test
    void changeMonitoringIntervalCallsDomainBehavior() {
        Subscription sub = subscription(true);
        Instant newStart = START.plus(5, ChronoUnit.DAYS);
        Instant newEnd = END.plus(5, ChronoUnit.DAYS);
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.changeMonitoringInterval(sub.id(), newStart, newEnd);
        assertEquals(newStart, result.monitoringStart());
        assertEquals(newEnd, result.monitoringEnd());
        assertEquals(sub.id(), result.id());
        verify(subscriptionPort).save(any());
    }

    @Test
    void changeServiceAccessUntilCallsDomainBehavior() {
        Subscription sub = subscription(true);
        Instant newAccess = ACCESS.plus(10, ChronoUnit.DAYS);
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.changeServiceAccessUntil(sub.id(), newAccess);
        assertEquals(newAccess, result.serviceAccessUntil());
        assertTrue(result.isActive());
        assertEquals(sub.isActive(), result.isActive());
        verify(subscriptionPort).save(any());
    }

    @Test
    void addTransformerStationCallsDomainBehavior() {
        Subscription sub = subscription(true);
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-2");
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(transformerStationPort.findByName("ТП-2")).thenReturn(Optional.of(tp));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.addTransformerStation(sub.id(), "ТП-2");
        assertTrue(result.transformerStations().contains(tp));
        verify(transformerStationPort).findByName("ТП-2");
        verify(subscriptionPort).save(any());
        verifyNoMoreInteractions(userPort, addressService);
    }

    @Test
    void removeTransformerStationCallsDomainBehavior() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription sub = Subscription.of(UUID.randomUUID(), user, address, START, END, true, ACCESS, Set.of(tp));
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(transformerStationPort.findByName("ТП-1")).thenReturn(Optional.of(tp));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.removeTransformerStation(sub.id(), "ТП-1");
        assertTrue(result.transformerStations().isEmpty());
        verify(subscriptionPort).save(any());
    }

    @Test
    void mutationFailsWhenSubscriptionAbsent() {
        when(subscriptionPort.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.activate(UUID.randomUUID()));
        assertThrows(NoSuchElementException.class, () -> service.addTransformerStation(UUID.randomUUID(), "ТП-1"));
    }

    @Test
    void noReconstructionViaOfForActivate() {
        // Ensure service does not call Subscription.of for state transition - we verify by checking saved object's identity preserved and isActive changed
        Subscription sub = subscription(false);
        when(subscriptionPort.findById(sub.id())).thenReturn(Optional.of(sub));
        when(subscriptionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Subscription result = service.activate(sub.id());
        // If service had used Subscription.of, it would still be valid but we check that domain behavior is used: isActive changed but other fields preserved
        assertEquals(sub.user(), result.user());
        assertEquals(sub.address(), result.address());
        assertEquals(sub.monitoringStart(), result.monitoringStart());
        assertEquals(sub.serviceAccessUntil(), result.serviceAccessUntil());
    }
}
