package com.zyibin.app.blackoutradar.domain.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

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

    @Test
    void validCreationWithoutTransformerStations() {
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user(), address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));

        assertTrue(subscription.isActive());
        assertEquals(NOW, subscription.monitoringStart());
        assertEquals(NOW.plus(30, ChronoUnit.DAYS), subscription.monitoringEnd());
        assertEquals(NOW.plus(90, ChronoUnit.DAYS), subscription.serviceAccessUntil());
        assertFalse(subscription.hasTransformerStations());
        assertTrue(subscription.transformerStations().isEmpty());
    }

    @Test
    void validCreationWithTransformerStations() {
        TransformerStation tp1 = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        TransformerStation tp2 = TransformerStation.of(UUID.randomUUID(), "ТП-2");
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user(), address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS),
                List.of(tp1, tp2));

        assertTrue(subscription.hasTransformerStations());
        assertEquals(Set.of(tp1, tp2), subscription.transformerStations());
    }

    @Test
    void nullUserRejected() {
        assertThrows(NullPointerException.class,
                () -> Subscription.of(UUID.randomUUID(), null, address(),
                        NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void nullAddressRejected() {
        assertThrows(NullPointerException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), null,
                        NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void nullMonitoringStartRejected() {
        assertThrows(NullPointerException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        null, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void nullMonitoringEndRejected() {
        assertThrows(NullPointerException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW, null, true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void startBeforeEndAccepted() {
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user(), address(),
                NOW, NOW.plusSeconds(1), true, NOW.plus(90, ChronoUnit.DAYS));

        assertTrue(subscription.monitoringStart().isBefore(subscription.monitoringEnd()));
    }

    @Test
    void startEqualToEndRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW, NOW, true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void startAfterEndRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW.plus(30, ChronoUnit.DAYS), NOW, true, NOW.plus(90, ChronoUnit.DAYS)));
    }

    @Test
    void nullServiceAccessUntilRejected() {
        assertThrows(NullPointerException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW, NOW.plus(30, ChronoUnit.DAYS), true, null));
    }

    @Test
    void inactiveStateIsExplicit() {
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user(), address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), false, NOW.plus(90, ChronoUnit.DAYS));

        assertFalse(subscription.isActive());
    }

    @Test
    void nullTransformerStationsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS), null));
    }

    @Test
    void duplicateTransformerStationsRejected() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");

        assertThrows(IllegalArgumentException.class,
                () -> Subscription.of(UUID.randomUUID(), user(), address(),
                        NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS),
                        List.of(tp, tp)));
    }

    @Test
    void transformerStationsCannotBeModifiedExternally() {
        TransformerStation tp = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user(), address(),
                NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS),
                List.of(tp));

        assertThrows(UnsupportedOperationException.class,
                () -> subscription.transformerStations().add(
                        TransformerStation.of(UUID.randomUUID(), "ТП-2")));
        assertThrows(UnsupportedOperationException.class,
                () -> subscription.transformerStations().clear());
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        User user = user();
        Address address = address();
        Subscription a = Subscription.of(
                id, user, address, NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));
        Subscription b = Subscription.of(
                id, user, address, NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS));

        assertNotEquals(a, Subscription.of(
                UUID.randomUUID(), user, address, NOW, NOW.plus(30, ChronoUnit.DAYS), true, NOW.plus(90, ChronoUnit.DAYS)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}