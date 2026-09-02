package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zyibin.app.blackoutradar.application.outage.OutageProcessingService;
import com.zyibin.app.blackoutradar.application.outage.ParsedOutage;
import com.zyibin.app.blackoutradar.application.provider.OutageProvider;
import com.zyibin.app.blackoutradar.application.provider.ProviderContext;
import com.zyibin.app.blackoutradar.application.provider.ProviderRegistry;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

@ExtendWith(MockitoExtension.class)
class SourceSchedulerTest {

    @Mock SourcePort sourcePort;
    @Mock ProviderRegistry providerRegistry;
    @Mock OutageProcessingService outageProcessingService;
    @Mock TaskScheduler taskScheduler;
    @Mock OutageProvider provider;

    SourceScheduler scheduler;

    Source activeSource;
    Source anotherActive;

    @BeforeEach
    void setUp() {
        scheduler = new SourceScheduler(sourcePort, providerRegistry, outageProcessingService, taskScheduler);
        activeSource = Source.of(UUID.randomUUID(), "srcA", "ТЕЛЕГРАМ", "typeA", "{\"k\":\"v\"}", "* * * * * *", true);
        anotherActive = Source.of(UUID.randomUUID(), "srcB", "ТЕЛЕГРАМ", "typeB", "0 * * * * *", true);
    }

    @Test
    void scheduleAllActiveSourcesUsesCronSchedule() {
        when(sourcePort.findAllActive()).thenReturn(List.of(activeSource, anotherActive));

        scheduler.scheduleAllActiveSources();

        verify(taskScheduler).schedule(any(Runnable.class), org.mockito.ArgumentMatchers.<Trigger>argThat(trigger -> trigger instanceof CronTrigger ct && ct.getExpression().equals(activeSource.schedule())));
        verify(taskScheduler).schedule(any(Runnable.class), org.mockito.ArgumentMatchers.<Trigger>argThat(trigger -> trigger instanceof CronTrigger ct && ct.getExpression().equals(anotherActive.schedule())));
        org.mockito.Mockito.verify(taskScheduler, org.mockito.Mockito.times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void executeSourceCreatesCorrectContextAndFetches() {
        ParsedOutage po = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        when(provider.fetch(any(ProviderContext.class))).thenReturn(List.of(po));

        scheduler.executeSource(activeSource);

        ArgumentCaptor<ProviderContext> ctxCaptor = ArgumentCaptor.forClass(ProviderContext.class);
        verify(provider).fetch(ctxCaptor.capture());
        ProviderContext ctx = ctxCaptor.getValue();
        assertEquals(activeSource.id(), ctx.sourceId());
        assertEquals(activeSource.configuration(), ctx.configuration());
        verify(outageProcessingService).process(po);
    }

    @Test
    void executeSourcePassesAllParsedOutagesToPipeline() {
        ParsedOutage po1 = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r1", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r2", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Мира", "10")));
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        when(provider.fetch(any())).thenReturn(List.of(po1, po2));

        scheduler.executeSource(activeSource);

        verify(outageProcessingService).process(po1);
        verify(outageProcessingService).process(po2);
    }

    @Test
    void emptyFetchDoesNotCallPipeline() {
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        when(provider.fetch(any())).thenReturn(List.of());

        scheduler.executeSource(activeSource);

        verify(outageProcessingService, never()).process(any());
    }

    @Test
    void providerNotFoundDoesNotThrowAndLogs() {
        when(providerRegistry.find("typeA")).thenReturn(Optional.empty());

        // should not throw
        scheduler.executeSource(activeSource);

        verify(provider, never()).fetch(any());
        verify(outageProcessingService, never()).process(any());
    }

    @Test
    void fetchExceptionIsolated() {
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        when(provider.fetch(any())).thenThrow(new RuntimeException("fetch failed"));

        scheduler.executeSource(activeSource);

        verify(outageProcessingService, never()).process(any());
        // no exception propagated
    }

    @Test
    void errorOneSourceDoesNotStopOthersViaProcessAllOnce() {
        Source sourceC = Source.of(UUID.randomUUID(), "srcC", "ТЕЛЕГРАМ", "typeC", null, "* * * * * *", true);
        when(sourcePort.findAllActive()).thenReturn(List.of(activeSource, anotherActive, sourceC));

        // activeSource -> provider not found
        when(providerRegistry.find("typeA")).thenReturn(Optional.empty());
        // anotherActive -> success
        OutageProvider providerB = org.mockito.Mockito.mock(OutageProvider.class);
        ParsedOutage po = new ParsedOutage(anotherActive.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        when(providerRegistry.find("typeB")).thenReturn(Optional.of(providerB));
        when(providerB.fetch(any())).thenReturn(List.of(po));
        // sourceC -> fetch exception
        OutageProvider providerC = org.mockito.Mockito.mock(OutageProvider.class);
        when(providerRegistry.find("typeC")).thenReturn(Optional.of(providerC));
        when(providerC.fetch(any())).thenThrow(new RuntimeException("c fail"));

        scheduler.processAllActiveSourcesOnce();

        verify(outageProcessingService).process(po);
        // ensure all three were attempted (no exception propagated)
        verify(providerRegistry).find("typeA");
        verify(providerRegistry).find("typeB");
        verify(providerRegistry).find("typeC");
    }

    @Test
    void processAllActiveSourcesOnceCallsPipelineForEach() {
        when(sourcePort.findAllActive()).thenReturn(List.of(activeSource));
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        ParsedOutage po = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        when(provider.fetch(any())).thenReturn(List.of(po));

        scheduler.processAllActiveSourcesOnce();

        verify(outageProcessingService).process(po);
    }

    @Test
    void noParallelExecutionForSameSource() throws Exception {
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch fetchContinue = new CountDownLatch(1);
        AtomicInteger fetchCount = new AtomicInteger();

        when(provider.fetch(any())).thenAnswer(inv -> {
            fetchCount.incrementAndGet();
            fetchStarted.countDown();
            try {
                fetchContinue.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ParsedOutage po = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
            return List.of(po);
        });

        Thread t1 = new Thread(() -> scheduler.executeSourceSafely(activeSource));
        t1.start();
        assertEquals(true, fetchStarted.await(5, TimeUnit.SECONDS), "fetch should have started");

        // Second call while first is running should be skipped
        scheduler.executeSourceSafely(activeSource);

        // fetch should have been called only once so far (second skipped)
        assertEquals(1, fetchCount.get());

        fetchContinue.countDown();
        t1.join(5000);

        // After first finishes, new call should be allowed
        scheduler.executeSourceSafely(activeSource);

        assertEquals(2, fetchCount.get());
        verify(provider, org.mockito.Mockito.times(2)).fetch(any());
    }

    @Test
    void pipelineExceptionDoesNotStopOtherOutages() {
        when(providerRegistry.find("typeA")).thenReturn(Optional.of(provider));
        ParsedOutage po1 = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r1", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15")));
        ParsedOutage po2 = new ParsedOutage(activeSource.id(), Instant.now(), Instant.now().plusSeconds(3600), "r2", null, List.of(new com.zyibin.app.blackoutradar.application.address.AddressInput("Омская область", null, null, "Омск", null, "ул Мира", "10")));
        when(provider.fetch(any())).thenReturn(List.of(po1, po2));
        doAnswer(inv -> { throw new RuntimeException("pipeline fail"); }).when(outageProcessingService).process(po1);

        scheduler.executeSource(activeSource);

        verify(outageProcessingService).process(po1);
        verify(outageProcessingService).process(po2);
    }
}
