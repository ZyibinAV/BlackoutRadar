package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DuplicateResolverConcurrencyTest {

    @Autowired private DuplicateResolver duplicateResolver;
    @Autowired private ParsedOutageProcessor parsedOutageProcessor;
    @Autowired private SourcePort sourcePort;
    @Autowired private PlatformTransactionManager transactionManager;

    @PersistenceContext private EntityManager entityManager;

    @Test
    void concurrentFallbackResolutionCreatesSinglePowerOutage() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Source source = sourcePort.save(Source.of(UUID.randomUUID(), "src-" + suffix, "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");

        // Create ParsedOutage with two addresses (canonical will be resolved)
        AddressInput in1 = new AddressInput("Регион-" + suffix, null, null, "Город-" + suffix, null, "ул Ленина-" + suffix, "15");
        AddressInput in2 = new AddressInput("Регион-" + suffix, null, null, "Город-" + suffix, null, "ул Мира-" + suffix, "10");
        ParsedOutage parsed = new ParsedOutage(source.id(), start, end, "Причина", null, List.of(in1, in2));

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<Future<DuplicateResolver.ResolutionResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        startLatch.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    try {
                        return template.execute(status -> {
                            List<Address> canonical = parsedOutageProcessor.resolveAddresses(parsed);
                            return duplicateResolver.resolve(parsed, canonical);
                        });
                    } finally {
                        done.countDown();
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers not ready");
            startLatch.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers not finished");

            Set<UUID> powerOutageIds = new HashSet<>();
            long createCount = 0;
            long ignoreCount = 0;
            long updateCount = 0;
            for (Future<DuplicateResolver.ResolutionResult> f : futures) {
                DuplicateResolver.ResolutionResult r = f.get(5, TimeUnit.SECONDS);
                powerOutageIds.add(r.powerOutage().id());
                if (r.decision() == DuplicateResolver.Decision.CREATE) createCount++;
                else if (r.decision() == DuplicateResolver.Decision.IGNORE) ignoreCount++;
                else if (r.decision() == DuplicateResolver.Decision.UPDATE) updateCount++;
            }
            assertEquals(1, powerOutageIds.size(), "All workers must get same PowerOutage id");
            assertEquals(1, createCount, "Exactly one CREATE");
            assertEquals(7, ignoreCount, "Seven IGNORE");
            assertEquals(0, updateCount, "No UPDATE for same data");

            // Physical DB verification: only one row for this source and startTime with those addresses
            TransactionTemplate verify = new TransactionTemplate(transactionManager);
            verify.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verify.execute(status -> {
                List<Address> canonical = parsedOutageProcessor.resolveAddresses(parsed);
                Set<UUID> ids = new java.util.HashSet<>();
                for (Address a : canonical) ids.add(a.id());
                DuplicateResolver.ResolutionResult r = duplicateResolver.resolve(parsed, canonical);
                assertEquals(powerOutageIds.iterator().next(), r.powerOutage().id());
                long totalForSource = ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM power_outage WHERE source_id = :sid")
                        .setParameter("sid", source.id()).getSingleResult()).longValue();
                assertEquals(1, totalForSource, "Only one PowerOutage for this source should exist");
                long addressRows = ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM power_outage_address WHERE power_outage_id = :pid")
                        .setParameter("pid", powerOutageIds.iterator().next()).getSingleResult()).longValue();
                assertEquals(2, addressRows, "power_outage_address rows must match unique addresses (2)");
                return null;
            });

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void concurrentExternalReferenceCreatesSinglePowerOutage() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Source source = sourcePort.save(Source.of(UUID.randomUUID(), "src-" + suffix, "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true));
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");
        String extRef = "ext-" + suffix;
        AddressInput in1 = new AddressInput("Регион-" + suffix, null, null, "Город-" + suffix, null, "ул Ленина-" + suffix, "15");
        ParsedOutage parsed = new ParsedOutage(source.id(), start, end, "Причина", extRef, List.of(in1));

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<Future<DuplicateResolver.ResolutionResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try { startLatch.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    try {
                        return template.execute(status -> {
                            List<Address> canonical = parsedOutageProcessor.resolveAddresses(parsed);
                            return duplicateResolver.resolve(parsed, canonical);
                        });
                    } finally {
                        done.countDown();
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            startLatch.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));

            Set<UUID> ids = new HashSet<>();
            long createCount = 0;
            long ignoreCount = 0;
            for (Future<DuplicateResolver.ResolutionResult> f : futures) {
                DuplicateResolver.ResolutionResult r = f.get(5, TimeUnit.SECONDS);
                ids.add(r.powerOutage().id());
                if (r.decision() == DuplicateResolver.Decision.CREATE) createCount++;
                else if (r.decision() == DuplicateResolver.Decision.IGNORE) ignoreCount++;
            }
            assertEquals(1, ids.size());
            assertEquals(1, createCount, "Exactly one CREATE for externalReference");
            assertEquals(7, ignoreCount, "Seven IGNORE");

            TransactionTemplate verify = new TransactionTemplate(transactionManager);
            verify.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verify.execute(status -> {
                long count = ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM power_outage WHERE source_id = :sid AND external_reference = :ref")
                        .setParameter("sid", source.id())
                        .setParameter("ref", extRef)
                        .getSingleResult()).longValue();
                assertEquals(1, count, "Only one row for externalReference");
                long addrRows = ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM power_outage_address WHERE power_outage_id = :pid")
                        .setParameter("pid", ids.iterator().next()).getSingleResult()).longValue();
                assertEquals(1, addrRows, "power_outage_address rows must be 1");
                return null;
            });
        } finally {
            executor.shutdown();
            try { if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
        }
    }
}
