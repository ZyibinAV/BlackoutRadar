package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.domain.address.Address;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class OutageProcessingService {

    private final ParsedOutageProcessor parsedOutageProcessor;
    private final DuplicateResolver duplicateResolver;

    public OutageProcessingService(ParsedOutageProcessor parsedOutageProcessor,
                                   DuplicateResolver duplicateResolver) {
        this.parsedOutageProcessor = Objects.requireNonNull(parsedOutageProcessor, "parsedOutageProcessor must not be null");
        this.duplicateResolver = Objects.requireNonNull(duplicateResolver, "duplicateResolver must not be null");
    }

    public DuplicateResolver.ResolutionResult process(ParsedOutage parsedOutage) {
        Objects.requireNonNull(parsedOutage, "parsedOutage must not be null");
        List<Address> canonicalAddresses = parsedOutageProcessor.resolveAddresses(parsedOutage);
        return duplicateResolver.resolve(parsedOutage, canonicalAddresses);
    }
}
