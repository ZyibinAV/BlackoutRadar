package com.zyibin.app.blackoutradar.application.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionSearchPort;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateFinderTest {

    @Mock
    private SubscriptionSearchPort searchPort;

    private CandidateFinder finder;

    private Address address1;
    private Address address2;
    private PowerOutage powerOutageWithOneAddress;
    private PowerOutage powerOutageWithTwoAddresses;
    private Subscription subscription1;
    private Subscription subscription2;

    @BeforeEach
    void setUp() {
        finder = new CandidateFinder(searchPort);

        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street1 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Street street2 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "МИРА");
        address1 = Address.of(UUID.randomUUID(), street1, new House("15", null, "15"));
        address2 = Address.of(UUID.randomUUID(), street2, new House("10", null, "10"));

        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant now = Instant.now();
        powerOutageWithOneAddress = PowerOutage.of(UUID.randomUUID(), source, now, now.plusSeconds(3600), "авария", "АКТИВНО",
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));
        powerOutageWithTwoAddresses = PowerOutage.of(UUID.randomUUID(), source, now, now.plusSeconds(3600), "авария", "АКТИВНО",
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address1), PowerOutageAddress.unboundOf(UUID.randomUUID(), address2)));

        User user = User.of(UUID.randomUUID(), "test@example.com", UserRole.USER, true);
        Instant start = now.minusSeconds(86400);
        Instant end = now.plusSeconds(86400);
        subscription1 = Subscription.of(UUID.randomUUID(), user, address1, start, end, true, end.plusSeconds(3600));
        subscription2 = Subscription.of(UUID.randomUUID(), user, address2, start, end, true, end.plusSeconds(3600));
    }

    @Test
    void findByOneAddressReturnsCandidate() {
        when(searchPort.findActiveByAddressIds(Set.of(address1.id()))).thenReturn(List.of(subscription1));

        List<Candidate> result = finder.findCandidates(powerOutageWithOneAddress);

        assertEquals(1, result.size());
        assertEquals(subscription1, result.get(0).subscription());

        ArgumentCaptor<Set<UUID>> captor = ArgumentCaptor.forClass(Set.class);
        verify(searchPort).findActiveByAddressIds(captor.capture());
        assertEquals(Set.of(address1.id()), captor.getValue());
    }

    @Test
    void findByMultipleAddressesPassesAllIds() {
        when(searchPort.findActiveByAddressIds(Set.of(address1.id(), address2.id()))).thenReturn(List.of(subscription1, subscription2));

        List<Candidate> result = finder.findCandidates(powerOutageWithTwoAddresses);

        assertEquals(2, result.size());
        ArgumentCaptor<Set<UUID>> captor = ArgumentCaptor.forClass(Set.class);
        verify(searchPort).findActiveByAddressIds(captor.capture());
        assertEquals(Set.of(address1.id(), address2.id()), captor.getValue());
    }

    @Test
    void resultContainsOnlySubscriptionsReturnedByPort() {
        when(searchPort.findActiveByAddressIds(Set.of(address1.id()))).thenReturn(List.of(subscription1));

        List<Candidate> result = finder.findCandidates(powerOutageWithOneAddress);

        assertTrue(result.stream().allMatch(c -> c.subscription().equals(subscription1)));
        assertEquals(1, result.size());
    }

    @Test
    void doesNotPerformFinalMatching() {
        // Subscription with different monitoring period / stations would be filtered by MatchingEngine,
        // but CandidateFinder must return them as-is if address matches.
        User user = User.of(UUID.randomUUID(), "other@example.com", UserRole.USER, true);
        // inactive flag is handled by search port, not by fine matching; but finder itself does not check transformer/monitoring
        // Create subscription that would fail final matching criteria, but finder should still return it.
        Instant farFutureStart = Instant.now().plusSeconds(100_000);
        Instant farFutureEnd = farFutureStart.plusSeconds(3600);
        Subscription farSubscription = Subscription.of(UUID.randomUUID(), user, address1, farFutureStart, farFutureEnd, true, farFutureEnd.plusSeconds(3600));

        when(searchPort.findActiveByAddressIds(Set.of(address1.id()))).thenReturn(List.of(farSubscription));

        List<Candidate> result = finder.findCandidates(powerOutageWithOneAddress);

        assertEquals(1, result.size());
        assertEquals(farSubscription, result.get(0).subscription());
    }

    @Test
    void emptyResultReturnsEmptyList() {
        when(searchPort.findActiveByAddressIds(Set.of(address1.id()))).thenReturn(List.of());

        List<Candidate> result = finder.findCandidates(powerOutageWithOneAddress);

        assertTrue(result.isEmpty());
    }

    @Test
    void wrapsEachSubscriptionInCandidate() {
        when(searchPort.findActiveByAddressIds(Set.of(address1.id(), address2.id()))).thenReturn(List.of(subscription1, subscription2));

        List<Candidate> result = finder.findCandidates(powerOutageWithTwoAddresses);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.subscription().equals(subscription1)));
        assertTrue(result.stream().anyMatch(c -> c.subscription().equals(subscription2)));
    }
}
