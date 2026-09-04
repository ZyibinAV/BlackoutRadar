package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.application.matching.Candidate;
import com.zyibin.app.blackoutradar.application.matching.CandidateFinder;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.matching.Match;
import com.zyibin.app.blackoutradar.domain.matching.MatchingEngine;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutageProcessingServiceTest {

    @Mock private ParsedOutageProcessor parsedOutageProcessor;
    @Mock private DuplicateResolver duplicateResolver;
    @Mock private CandidateFinder candidateFinder;
    @Mock private MatchingEngine matchingEngine;

    private OutageProcessingService service;

    private ParsedOutage parsedOutage;
    private Address address1;
    private Address address2;
    private PowerOutage powerOutage;
    private Subscription subscription1;
    private Subscription subscription2;

    @BeforeEach
    void setUp() {
        service = new OutageProcessingService(parsedOutageProcessor, duplicateResolver,
                candidateFinder, matchingEngine);

        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        AddressInput input = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        parsedOutage = new ParsedOutage(source.id(), start, end, "reason", "ext-1", List.of(input));

        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street1 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Street street2 = Street.of(UUID.randomUUID(), city, StreetType.STREET, "МИРА");
        address1 = Address.of(UUID.randomUUID(), street1, new House("15", null, "15"));
        address2 = Address.of(UUID.randomUUID(), street2, new House("10", null, "10"));

        powerOutage = PowerOutage.of(UUID.randomUUID(), source, start, end, "reason", "АКТИВНО",
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address1)));

        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Instant serviceAccessUntil = end.plusSeconds(3600);
        subscription1 = Subscription.of(UUID.randomUUID(), user, address1, start, end, true, serviceAccessUntil);
        subscription2 = Subscription.of(UUID.randomUUID(), user, address2, start, end, true, serviceAccessUntil);
    }

    @Test
    void processCreateRunsFullPipelineInOrderAndReturnsMatches() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult resolution =
                new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.CREATE, powerOutage);
        List<Candidate> candidates = List.of(new Candidate(subscription1));
        List<Match> matches = List.of(new Match(subscription1, powerOutage));

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(resolution);
        when(candidateFinder.findCandidates(same(powerOutage))).thenReturn(candidates);
        when(matchingEngine.match(same(powerOutage), eq(List.of(subscription1)))).thenReturn(matches);

        List<Match> result = service.process(parsedOutage);

        assertSame(matches, result);
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver, candidateFinder, matchingEngine);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        inOrder.verify(candidateFinder).findCandidates(same(powerOutage));
        inOrder.verify(matchingEngine).match(same(powerOutage), eq(List.of(subscription1)));
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver, candidateFinder, matchingEngine);
    }

    @Test
    void processUpdateAlsoRunsMatching() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult resolution =
                new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.UPDATE, powerOutage);
        List<Match> matches = List.of(new Match(subscription1, powerOutage));

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(resolution);
        when(candidateFinder.findCandidates(same(powerOutage))).thenReturn(List.of(new Candidate(subscription1)));
        when(matchingEngine.match(same(powerOutage), eq(List.of(subscription1)))).thenReturn(matches);

        List<Match> result = service.process(parsedOutage);

        assertSame(matches, result);
        verify(candidateFinder).findCandidates(same(powerOutage));
        verify(matchingEngine).match(same(powerOutage), eq(List.of(subscription1)));
    }

    @Test
    void processIgnoreReturnsEmptyAndSkipsMatching() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult resolution =
                new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.IGNORE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(resolution);

        List<Match> result = service.process(parsedOutage);

        assertTrue(result.isEmpty());
        verifyNoInteractions(candidateFinder, matchingEngine);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void candidatesAreConvertedToSubscriptionsForEngine() {
        List<Address> canonical = List.of(address1, address2);
        DuplicateResolver.ResolutionResult resolution =
                new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.CREATE, powerOutage);
        List<Candidate> candidates = List.of(new Candidate(subscription1), new Candidate(subscription2));

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(resolution);
        when(candidateFinder.findCandidates(same(powerOutage))).thenReturn(candidates);
        when(matchingEngine.match(same(powerOutage), eq(List.of(subscription1, subscription2)))).thenReturn(List.of());

        service.process(parsedOutage);

        verify(matchingEngine).match(same(powerOutage), eq(List.of(subscription1, subscription2)));
    }

    @Test
    void emptyCandidatesPassedToEngineAsEmptyList() {
        List<Address> canonical = List.of(address1);
        DuplicateResolver.ResolutionResult resolution =
                new DuplicateResolver.ResolutionResult(DuplicateResolver.Decision.CREATE, powerOutage);

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenReturn(resolution);
        when(candidateFinder.findCandidates(same(powerOutage))).thenReturn(List.of());
        when(matchingEngine.match(same(powerOutage), eq(List.of()))).thenReturn(List.of());

        List<Match> result = service.process(parsedOutage);

        assertTrue(result.isEmpty());
        verify(matchingEngine).match(same(powerOutage), eq(List.of()));
    }

    @Test
    void whenProcessorThrowsNothingElseCalled() {
        RuntimeException ex = new RuntimeException("processor failure");
        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.process(parsedOutage));

        assertSame(ex, thrown);
        verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        verifyNoInteractions(duplicateResolver, candidateFinder, matchingEngine);
        verifyNoMoreInteractions(parsedOutageProcessor);
    }

    @Test
    void whenResolverThrowsMatchingNotStarted() {
        List<Address> canonical = List.of(address1);
        RuntimeException ex = new IllegalStateException("resolver failure");

        when(parsedOutageProcessor.resolveAddresses(parsedOutage)).thenReturn(canonical);
        when(duplicateResolver.resolve(parsedOutage, canonical)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.process(parsedOutage));

        assertSame(ex, thrown);
        InOrder inOrder = inOrder(parsedOutageProcessor, duplicateResolver);
        inOrder.verify(parsedOutageProcessor).resolveAddresses(parsedOutage);
        inOrder.verify(duplicateResolver).resolve(parsedOutage, canonical);
        verifyNoInteractions(candidateFinder, matchingEngine);
        verifyNoMoreInteractions(parsedOutageProcessor, duplicateResolver);
    }

    @Test
    void processRequiresNonNullParsedOutage() {
        assertThrows(NullPointerException.class, () -> service.process(null));
        verifyNoInteractions(parsedOutageProcessor, duplicateResolver, candidateFinder, matchingEngine);
    }

    @Test
    void temporaryMatchingBoundaryNotInPipeline() {
        Stream.of(OutageProcessingService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .forEach(typeName -> assertFalse(typeName.contains("MatchingBoundary"),
                        "OutageProcessingService must not depend on " + typeName));
        Stream.of(OutageProcessingService.class.getDeclaredConstructors())
                .flatMap(constructor -> Stream.of(constructor.getParameterTypes()))
                .map(Class::getName)
                .forEach(typeName -> {
                    assertFalse(typeName.contains("MatchingBoundary"),
                            "OutageProcessingService must not depend on " + typeName);
                    assertFalse(typeName.contains("NoOp"),
                            "OutageProcessingService must not depend on " + typeName);
                });
        Stream.of(OutageProcessingService.class.getDeclaredMethods())
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()),
                        Stream.of(method.getParameterTypes())))
                .map(Class::getName)
                .forEach(typeName -> assertFalse(typeName.contains("MatchingBoundary"),
                        "OutageProcessingService must not depend on " + typeName));
    }
}
