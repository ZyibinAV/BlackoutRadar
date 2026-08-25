package com.zyibin.app.blackoutradar.domain.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionBehaviorTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plus(30, ChronoUnit.DAYS);
    private static final Instant ACCESS = START.plus(90, ChronoUnit.DAYS);

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private Address address() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        House house = new House("15", null, "15");
        return Address.of(UUID.randomUUID(), street, house);
    }

    private Subscription base(boolean active) {
        return Subscription.of(UUID.randomUUID(), user(), address(), START, END, active, ACCESS);
    }

    @Test
    void activateInactiveSubscription() {
        Subscription s = base(false);
        Subscription activated = s.activate();
        assertTrue(activated.isActive());
        assertEquals(s.id(), activated.id());
        assertFalse(s.isActive());
    }

    @Test
    void deactivateActiveSubscription() {
        Subscription s = base(true);
        Subscription deactivated = s.deactivate();
        assertFalse(deactivated.isActive());
        assertTrue(s.isActive());
    }

    @Test
    void repeatedActivateKeepsActive() {
        Subscription s = base(true);
        Subscription again = s.activate();
        assertTrue(again.isActive());
        assertEquals(s.id(), again.id());
        assertEquals(s.monitoringStart(), again.monitoringStart());
        assertEquals(s.serviceAccessUntil(), again.serviceAccessUntil());
    }

    @Test
    void repeatedDeactivateKeepsInactive() {
        Subscription s = base(false);
        Subscription again = s.deactivate();
        assertFalse(again.isActive());
        assertEquals(s.id(), again.id());
    }

    @Test
    void changeValidMonitoringInterval() {
        Subscription s = base(true);
        Instant newStart = START.plus(10, ChronoUnit.DAYS);
        Instant newEnd = END.plus(10, ChronoUnit.DAYS);
        Subscription changed = s.withMonitoringInterval(newStart, newEnd);
        assertEquals(newStart, changed.monitoringStart());
        assertEquals(newEnd, changed.monitoringEnd());
        assertEquals(s.id(), changed.id());
        assertEquals(s.user(), changed.user());
        assertEquals(s.address(), changed.address());
        assertEquals(s.isActive(), changed.isActive());
        assertEquals(s.serviceAccessUntil(), changed.serviceAccessUntil());
        assertEquals(s.transformerStations(), changed.transformerStations());
    }

    @Test
    void rejectionInvalidMonitoringInterval() {
        Subscription s = base(true);
        assertThrows(IllegalArgumentException.class, () -> s.withMonitoringInterval(END, START));
        assertThrows(IllegalArgumentException.class, () -> s.withMonitoringInterval(START, START));
        assertThrows(NullPointerException.class, () -> s.withMonitoringInterval(null, END));
        assertThrows(NullPointerException.class, () -> s.withMonitoringInterval(START, null));
    }

    @Test
    void changeServiceAccessUntil() {
        Subscription s = base(true);
        Instant newAccess = ACCESS.plus(30, ChronoUnit.DAYS);
        Subscription changed = s.withServiceAccessUntil(newAccess);
        assertEquals(newAccess, changed.serviceAccessUntil());
        assertEquals(s.id(), changed.id());
    }

    @Test
    void changeServiceAccessUntilDoesNotChangeIsActive() {
        Subscription active = base(true);
        Subscription changedActive = active.withServiceAccessUntil(ACCESS.plus(1, ChronoUnit.DAYS));
        assertTrue(changedActive.isActive());
        Subscription inactive = base(false);
        Subscription changedInactive = inactive.withServiceAccessUntil(ACCESS.plus(1, ChronoUnit.DAYS));
        assertFalse(changedInactive.isActive());
        assertThrows(NullPointerException.class, () -> active.withServiceAccessUntil(null));
    }

    @Test
    void expiredServiceAccessUntilDoesNotCauseAutomaticDeactivation() {
        Instant past = START.minus(10, ChronoUnit.DAYS);
        Subscription s = Subscription.of(UUID.randomUUID(), user(), address(), START, END, true, past);
        assertTrue(s.isActive());
        Subscription changed = s.withServiceAccessUntil(past);
        assertTrue(changed.isActive());
    }

    @Test
    void addTransformerStation() {
        Subscription s = base(true);
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription added = s.addTransformerStation(tp);
        assertTrue(added.transformerStations().contains(tp));
        assertEquals(1, added.transformerStations().size());
        assertTrue(s.transformerStations().isEmpty());
    }

    @Test
    void duplicateTransformerStationRejected() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription s = Subscription.of(UUID.randomUUID(), user(), address(), START, END, true, ACCESS, List.of(tp));
        assertThrows(IllegalArgumentException.class, () -> s.addTransformerStation(tp));
        // also duplicate via same id different name
        TransformerStation dup = TransformerStation.of(tp.id(), "ТП-2");
        assertThrows(IllegalArgumentException.class, () -> s.addTransformerStation(dup));
        assertThrows(NullPointerException.class, () -> s.addTransformerStation(null));
    }

    @Test
    void removeTransformerStation() {
        TransformerStation tp1 = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        TransformerStation tp2 = TransformerStation.of(UUID.randomUUID(), "ТП-2");
        Subscription s = Subscription.of(UUID.randomUUID(), user(), address(), START, END, true, ACCESS, List.of(tp1, tp2));
        Subscription removed = s.removeTransformerStation(tp1);
        assertEquals(1, removed.transformerStations().size());
        assertFalse(removed.transformerStations().contains(tp1));
        assertTrue(removed.transformerStations().contains(tp2));
        assertEquals(2, s.transformerStations().size());
    }

    @Test
    void removeLastTransformerStationAllowed() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription s = Subscription.of(UUID.randomUUID(), user(), address(), START, END, true, ACCESS, List.of(tp));
        Subscription removed = s.removeTransformerStation(tp);
        assertTrue(removed.transformerStations().isEmpty());
        assertFalse(removed.hasTransformerStations());
        assertEquals(1, s.transformerStations().size());
    }

    @Test
    void immutabilityOriginalSubscription() {
        Subscription s = base(true);
        Instant originalStart = s.monitoringStart();
        Instant originalEnd = s.monitoringEnd();
        Instant originalAccess = s.serviceAccessUntil();
        boolean originalActive = s.isActive();

        Subscription a = s.activate();
        assertNotSame(s, a);
        assertEquals(originalStart, s.monitoringStart());
        assertEquals(originalEnd, s.monitoringEnd());
        assertEquals(originalAccess, s.serviceAccessUntil());
        assertEquals(originalActive, s.isActive());

        Subscription b = s.withMonitoringInterval(START.plus(1, ChronoUnit.DAYS), END.plus(1, ChronoUnit.DAYS));
        assertEquals(originalStart, s.monitoringStart());

        Subscription c = s.withServiceAccessUntil(ACCESS.plus(1, ChronoUnit.DAYS));
        assertEquals(originalAccess, s.serviceAccessUntil());

        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription d = s.addTransformerStation(tp);
        assertTrue(s.transformerStations().isEmpty());
        assertEquals(originalActive, s.isActive());

        assertThrows(UnsupportedOperationException.class, () -> s.transformerStations().add(tp));
    }

    @Test
    void preservationOfOtherFieldsOnEachTransition() {
        User user = user();
        Address addr = address();
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription s = Subscription.of(UUID.randomUUID(), user, addr, START, END, false, ACCESS, List.of(tp));

        Subscription activated = s.activate();
        assertEquals(s.id(), activated.id());
        assertEquals(s.user(), activated.user());
        assertEquals(s.address(), activated.address());
        assertEquals(s.monitoringStart(), activated.monitoringStart());
        assertEquals(s.monitoringEnd(), activated.monitoringEnd());
        assertEquals(s.serviceAccessUntil(), activated.serviceAccessUntil());
        assertEquals(s.transformerStations(), activated.transformerStations());

        Instant newStart = START.plus(5, ChronoUnit.DAYS);
        Instant newEnd = END.plus(5, ChronoUnit.DAYS);
        Subscription changedInterval = s.withMonitoringInterval(newStart, newEnd);
        assertEquals(s.id(), changedInterval.id());
        assertEquals(s.user(), changedInterval.user());
        assertEquals(s.address(), changedInterval.address());
        assertEquals(s.isActive(), changedInterval.isActive());
        assertEquals(s.serviceAccessUntil(), changedInterval.serviceAccessUntil());

        Instant newAccess = ACCESS.plus(5, ChronoUnit.DAYS);
        Subscription changedAccess = s.withServiceAccessUntil(newAccess);
        assertEquals(s.id(), changedAccess.id());
        assertEquals(s.user(), changedAccess.user());
        assertEquals(s.address(), changedAccess.address());
        assertEquals(s.monitoringStart(), changedAccess.monitoringStart());
        assertEquals(s.monitoringEnd(), changedAccess.monitoringEnd());
        assertEquals(s.isActive(), changedAccess.isActive());
        assertEquals(s.transformerStations(), changedAccess.transformerStations());

        TransformerStation tp2 = TransformerStation.of(UUID.randomUUID(), "ТП-2");
        Subscription added = s.addTransformerStation(tp2);
        assertEquals(s.id(), added.id());
        assertEquals(s.user(), added.user());
        assertEquals(s.monitoringStart(), added.monitoringStart());

        Subscription removed = s.removeTransformerStation(tp);
        assertEquals(s.id(), removed.id());
        assertTrue(removed.transformerStations().isEmpty());
    }

    @Test
    void removeNonExistentStationRejected() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription s = base(true);
        assertThrows(IllegalArgumentException.class, () -> s.removeTransformerStation(tp));
        assertThrows(NullPointerException.class, () -> s.removeTransformerStation(null));
    }
}
