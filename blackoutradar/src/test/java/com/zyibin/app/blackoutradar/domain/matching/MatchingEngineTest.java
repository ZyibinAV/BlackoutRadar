package com.zyibin.app.blackoutradar.domain.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private static final Instant SUB_START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant SUB_END = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant OUT_START = Instant.parse("2026-01-01T11:00:00Z");
    private static final Instant OUT_END = Instant.parse("2026-01-01T13:00:00Z");

    private final MatchingEngine engine = new MatchingEngine();

    private User user() {
        return User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
    }

    private Source source() {
        return Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
    }

    private Address newAddress() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        return Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }

    private Address sameContentDifferentIdAddress() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        return Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }

    private TransformerStation station(String name) {
        return TransformerStation.of(UUID.randomUUID(), name);
    }

    private Subscription subscription(Address address, Instant monitoringStart, Instant monitoringEnd,
                                      boolean active, Instant serviceAccessUntil,
                                      List<TransformerStation> stations) {
        return Subscription.of(UUID.randomUUID(), user(), address,
                monitoringStart, monitoringEnd, active, serviceAccessUntil, stations);
    }

    private Subscription subscription(Address address, boolean active) {
        return subscription(address, SUB_START, SUB_END, active,
                SUB_END.plusSeconds(3600), List.of());
    }

    private PowerOutage outage(Instant start, Instant end, List<PowerOutageAddress> addresses) {
        return PowerOutage.of(UUID.randomUUID(), source(), start, end,
                "Аварийное отключение", "АКТИВНО", addresses);
    }

    private PowerOutage outageWith(Address address) {
        return outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
    }

    // 1. active Subscription → может создать Match
    @Test
    void activeSubscriptionCreatesMatch() {
        Address address = newAddress();
        PowerOutage outage = outageWith(address);
        Subscription candidate = subscription(address, true);

        List<Match> result = engine.match(outage, List.of(candidate));

        assertEquals(1, result.size());
        assertEquals(candidate, result.get(0).subscription());
        assertEquals(outage, result.get(0).powerOutage());
    }

    // 2. inactive Subscription → Match отсутствует
    @Test
    void inactiveSubscriptionCreatesNoMatch() {
        Address address = newAddress();
        PowerOutage outage = outageWith(address);
        Subscription candidate = subscription(address, false);

        assertTrue(engine.match(outage, List.of(candidate)).isEmpty());
    }

    // 3. совпадающий canonical Address → Match
    @Test
    void matchingCanonicalAddressCreatesMatch() {
        Address address = newAddress();
        PowerOutage outage = outageWith(address);

        List<Match> result = engine.match(outage, List.of(subscription(address, true)));

        assertEquals(1, result.size());
    }

    // 4. другой Address → Match отсутствует
    @Test
    void differentAddressCreatesNoMatch() {
        PowerOutage outage = outageWith(newAddress());
        Subscription candidate = subscription(newAddress(), true);

        assertTrue(engine.match(outage, List.of(candidate)).isEmpty());
    }

    // 5. не использовать совпадение строковых представлений Address
    @Test
    void sameStringContentButDifferentIdsCreatesNoMatch() {
        Address outageAddress = sameContentDifferentIdAddress();
        Address subscriptionAddress = sameContentDifferentIdAddress();
        PowerOutage outage = outageWith(outageAddress);
        Subscription candidate = subscription(subscriptionAddress, true);

        assertTrue(engine.match(outage, List.of(candidate)).isEmpty());
    }

    // 6. обе стороны без станции → Match
    @Test
    void bothSidesWithoutStationCreatesMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of());

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 7. Subscription без станции, PowerOutageAddress со станцией → Match
    @Test
    void subscriptionWithoutStationOutageWithStationCreatesMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address, station("ТП-1"))));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of());

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 8. Subscription со станцией, PowerOutageAddress без станции → Match
    @Test
    void subscriptionWithStationOutageWithoutStationCreatesMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of(station("ТП-1")));

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 9. обе стороны со станциями и есть совпадение → Match
    @Test
    void bothSidesWithSameStationCreatesMatch() {
        Address address = newAddress();
        TransformerStation shared = station("ТП-1");
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address, shared)));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of(shared));

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 10. обе стороны со станциями и совпадения нет → Match отсутствует
    @Test
    void bothSidesWithDifferentStationsCreatesNoMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address, station("ТП-2"))));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of(station("ТП-1")));

        assertTrue(engine.match(outage, List.of(sub)).isEmpty());
    }

    @Test
    void stationComparedByIdNotByName() {
        Address address = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address, station("ТП-1"))));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of(station("ТП-1")));

        assertTrue(engine.match(outage, List.of(sub)).isEmpty());
    }

    // 11. Subscription содержит несколько станций, одна совпадает → Match
    @Test
    void subscriptionWithMultipleStationsOneMatchingCreatesMatch() {
        Address address = newAddress();
        TransformerStation matching = station("ТП-2");
        PowerOutage outage = outage(OUT_START, OUT_END,
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address, matching)));
        Subscription sub = subscription(address, SUB_START, SUB_END, true,
                SUB_END.plusSeconds(3600), List.of(station("ТП-1"), matching, station("ТП-3")));

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 12. несколько PowerOutageAddress для одного Address → не более одного Match
    @Test
    void multipleOutageAddressesYieldAtMostOneMatch() {
        Address matching = newAddress();
        Address other = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END, List.of(
                PowerOutageAddress.unboundOf(UUID.randomUUID(), other),
                PowerOutageAddress.unboundOf(UUID.randomUUID(), matching)));
        Subscription sub = subscription(matching, true);

        List<Match> result = engine.match(outage, List.of(sub));

        assertEquals(1, result.size());
    }

    // 13. периоды имеют обычное пересечение → Match
    @Test
    void overlappingPeriodsCreateMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(
                Instant.parse("2026-01-01T11:00:00Z"), Instant.parse("2026-01-01T13:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address,
                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"),
                true, Instant.parse("2026-01-01T20:00:00Z"), List.of());

        assertEquals(1, engine.match(outage, List.of(sub)).size());
    }

    // 14. Subscription заканчивается ровно в момент начала PowerOutage → отсутствует
    @Test
    void subscriptionEndsExactlyAtOutageStartCreatesNoMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(
                Instant.parse("2026-01-01T12:00:00Z"), Instant.parse("2026-01-01T13:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address, SUB_START, SUB_END,
                true, SUB_END.plusSeconds(3600), List.of());

        assertTrue(engine.match(outage, List.of(sub)).isEmpty());
    }

    // 15. PowerOutage заканчивается ровно в момент начала Subscription → отсутствует
    @Test
    void outageEndsExactlyAtSubscriptionStartCreatesNoMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(
                Instant.parse("2026-01-01T08:00:00Z"), Instant.parse("2026-01-01T10:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address, SUB_START, SUB_END,
                true, SUB_END.plusSeconds(3600), List.of());

        assertTrue(engine.match(outage, List.of(sub)).isEmpty());
    }

    // 16. периоды не пересекаются → Match отсутствует
    @Test
    void disjointPeriodsCreateNoMatch() {
        Address address = newAddress();
        PowerOutage outage = outage(
                Instant.parse("2026-01-01T13:00:00Z"), Instant.parse("2026-01-01T14:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription sub = subscription(address, SUB_START, SUB_END,
                true, SUB_END.plusSeconds(3600), List.of());

        assertTrue(engine.match(outage, List.of(sub)).isEmpty());
    }

    // 17. один период полностью содержит другой → Match
    @Test
    void containingPeriodsCreateMatch() {
        Address address = newAddress();
        Subscription wide = subscription(address,
                Instant.parse("2026-01-01T09:00:00Z"), Instant.parse("2026-01-01T14:00:00Z"),
                true, Instant.parse("2026-01-01T20:00:00Z"), List.of());
        PowerOutage narrow = outage(
                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T11:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        assertEquals(1, engine.match(narrow, List.of(wide)).size());

        Subscription narrowSub = subscription(address,
                Instant.parse("2026-01-01T10:30:00Z"), Instant.parse("2026-01-01T11:00:00Z"),
                true, Instant.parse("2026-01-01T20:00:00Z"), List.of());
        PowerOutage wideOutage = outage(
                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        assertEquals(1, engine.match(wideOutage, List.of(narrowSub)).size());
    }

    // 18. serviceAccessUntil не влияет на результат
    @Test
    void serviceAccessUntilDoesNotAffectMatching() {
        Address address = newAddress();
        PowerOutage overlapping = outageWith(address);
        Subscription expiredAccessButOverlappingMonitoring = subscription(address, SUB_START, SUB_END,
                true, Instant.parse("2020-01-01T00:00:00Z"), List.of());
        assertEquals(1, engine.match(overlapping,
                List.of(expiredAccessButOverlappingMonitoring)).size());

        PowerOutage disjoint = outage(
                Instant.parse("2026-01-01T13:00:00Z"), Instant.parse("2026-01-01T14:00:00Z"),
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription coveringAccessButDisjointMonitoring = subscription(address, SUB_START, SUB_END,
                true, Instant.parse("2026-01-01T14:00:00Z"), List.of());
        assertTrue(engine.match(disjoint,
                List.of(coveringAccessButDisjointMonitoring)).isEmpty());
    }

    // 19. пустой список Subscription → пустой список Match
    @Test
    void emptyCandidatesReturnEmptyMatches() {
        assertTrue(engine.match(outageWith(newAddress()), List.of()).isEmpty());
    }

    // 20. один PowerOutage соответствует нескольким Subscription → несколько Match
    @Test
    void oneOutageMatchesMultipleSubscriptions() {
        Address address1 = newAddress();
        Address address2 = newAddress();
        PowerOutage outage = outage(OUT_START, OUT_END, List.of(
                PowerOutageAddress.unboundOf(UUID.randomUUID(), address1),
                PowerOutageAddress.unboundOf(UUID.randomUUID(), address2)));
        Subscription first = subscription(address1, true);
        Subscription second = subscription(address2, true);

        List<Match> result = engine.match(outage, List.of(first, second));

        assertEquals(2, result.size());
    }

    // 21. одна Subscription + один PowerOutage → максимум один Match
    @Test
    void oneSubscriptionYieldsAtMostOneMatch() {
        Address address = newAddress();
        PowerOutage outage = outageWith(address);
        Subscription sub = subscription(address, true);

        List<Match> result = engine.match(outage, List.of(sub, sub));

        assertEquals(1, result.size());
    }

    @Test
    void matchRequiresSubscriptionAndOutage() {
        Address address = newAddress();
        Subscription sub = subscription(address, true);
        PowerOutage outage = outageWith(address);

        assertThrows(NullPointerException.class, () -> new Match(null, outage));
        assertThrows(NullPointerException.class, () -> new Match(sub, null));
    }

    @Test
    void nullInputsRejected() {
        Address address = newAddress();
        PowerOutage outage = outageWith(address);
        Subscription candidate = subscription(address, true);

        assertThrows(NullPointerException.class, () -> engine.match(null, List.of(candidate)));
        assertThrows(NullPointerException.class, () -> engine.match(outage, null));
    }
}
