package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.application.matching.Candidate;
import com.zyibin.app.blackoutradar.application.matching.CandidateFinder;
import com.zyibin.app.blackoutradar.application.notification.NotificationMessageFactory;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.matching.Match;
import com.zyibin.app.blackoutradar.domain.matching.MatchingEngine;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutageProcessingService {

    private final ParsedOutageProcessor parsedOutageProcessor;
    private final DuplicateResolver duplicateResolver;
    private final CandidateFinder candidateFinder;
    private final MatchingEngine matchingEngine;
    private final NotificationMessageFactory notificationMessageFactory;
    private final NotificationPort notificationPort;

    public OutageProcessingService(ParsedOutageProcessor parsedOutageProcessor,
                                   DuplicateResolver duplicateResolver,
                                   CandidateFinder candidateFinder,
                                   MatchingEngine matchingEngine,
                                   NotificationMessageFactory notificationMessageFactory,
                                   NotificationPort notificationPort) {
        this.parsedOutageProcessor = Objects.requireNonNull(parsedOutageProcessor, "parsedOutageProcessor must not be null");
        this.duplicateResolver = Objects.requireNonNull(duplicateResolver, "duplicateResolver must not be null");
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder must not be null");
        this.matchingEngine = Objects.requireNonNull(matchingEngine, "matchingEngine must not be null");
        this.notificationMessageFactory = Objects.requireNonNull(notificationMessageFactory, "notificationMessageFactory must not be null");
        this.notificationPort = Objects.requireNonNull(notificationPort, "notificationPort must not be null");
    }

    public List<Match> process(ParsedOutage parsedOutage) {
        Objects.requireNonNull(parsedOutage, "parsedOutage must not be null");
        List<Address> canonicalAddresses = parsedOutageProcessor.resolveAddresses(parsedOutage);
        DuplicateResolver.ResolutionResult resolution = duplicateResolver.resolve(parsedOutage, canonicalAddresses);
        if (resolution.decision() == DuplicateResolver.Decision.IGNORE) {
            return List.of();
        }
        PowerOutage powerOutage = resolution.powerOutage();
        List<Subscription> subscriptions = candidateFinder.findCandidates(powerOutage).stream()
                .map(Candidate::subscription)
                .toList();
        List<Match> matches = matchingEngine.match(powerOutage, subscriptions);
        for (Match match : matches) {
            UUID subscriptionId = match.subscription().id();
            UUID powerOutageId = match.powerOutage().id();
            if (notificationPort.findBySubscriptionAndPowerOutage(subscriptionId, powerOutageId).isPresent()) {
                continue;
            }
            String message = notificationMessageFactory.createMessage(match.powerOutage());
            Notification notification = Notification.of(UUID.randomUUID(),
                    match.subscription(), match.powerOutage(), message);
            notificationPort.save(notification);
        }
        return matches;
    }
}
