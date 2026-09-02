package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import com.zyibin.app.blackoutradar.application.outage.OutageProcessingService;
import com.zyibin.app.blackoutradar.application.outage.ParsedOutage;
import com.zyibin.app.blackoutradar.application.provider.OutageProvider;
import com.zyibin.app.blackoutradar.application.provider.ProviderContext;
import com.zyibin.app.blackoutradar.application.provider.ProviderRegistry;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class SourceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceScheduler.class);

    private final SourcePort sourcePort;
    private final ProviderRegistry providerRegistry;
    private final OutageProcessingService outageProcessingService;
    private final TaskScheduler taskScheduler;

    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public SourceScheduler(SourcePort sourcePort,
                           ProviderRegistry providerRegistry,
                           OutageProcessingService outageProcessingService,
                           TaskScheduler taskScheduler) {
        this.sourcePort = sourcePort;
        this.providerRegistry = providerRegistry;
        this.outageProcessingService = outageProcessingService;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void scheduleAllActiveSources() {
        List<Source> activeSources;
        try {
            activeSources = sourcePort.findAllActive();
        } catch (Exception e) {
            log.error("Failed to load active sources for scheduling", e);
            return;
        }
        for (Source source : activeSources) {
            try {
                scheduleSource(source);
            } catch (Exception e) {
                log.error("Failed to schedule source {}", source.id(), e);
            }
        }
    }

    void scheduleSource(Source source) {
        CronTrigger trigger = new CronTrigger(source.schedule());
        ScheduledFuture<?> future = taskScheduler.schedule(() -> executeSourceSafely(source), trigger);
        if (future != null) {
            ScheduledFuture<?> previous = scheduled.put(source.id(), future);
            if (previous != null) {
                previous.cancel(false);
            }
        }
    }

    void executeSourceSafely(Source source) {
        if (!running.add(source.id())) {
            log.warn("Source {} is already running, skipping concurrent execution", source.id());
            return;
        }
        try {
            executeSource(source);
        } finally {
            running.remove(source.id());
        }
    }

    void executeSource(Source source) {
        try {
            Optional<OutageProvider> providerOpt = providerRegistry.find(source.providerType());
            if (providerOpt.isEmpty()) {
                log.error("Provider not found for source {} with providerType {}", source.id(), source.providerType());
                return;
            }
            OutageProvider provider = providerOpt.get();
            ProviderContext context = new ProviderContext(source.id(), source.configuration());
            List<ParsedOutage> outages;
            try {
                outages = provider.fetch(context);
            } catch (Exception e) {
                log.error("Provider fetch failed for source {}", source.id(), e);
                return;
            }
            if (outages == null || outages.isEmpty()) {
                return;
            }
            for (ParsedOutage parsedOutage : outages) {
                try {
                    outageProcessingService.process(parsedOutage);
                } catch (Exception e) {
                    log.error("Failed to process ParsedOutage {} for source {}", parsedOutage, source.id(), e);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error while processing source {}", source.id(), e);
        }
    }

    /**
     * Synchronous processing of all active sources on demand.
     * Used for testing and manual triggering.
     * Each source is processed with error isolation and single-execution guard.
     */
    public void processAllActiveSourcesOnce() {
        List<Source> activeSources = sourcePort.findAllActive();
        for (Source source : activeSources) {
            executeSourceSafely(source);
        }
    }

    // package-private for testing
    boolean isRunning(UUID sourceId) {
        return running.contains(sourceId);
    }

    int scheduledCount() {
        return scheduled.size();
    }
}
