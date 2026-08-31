package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import org.springframework.stereotype.Component;

@Component
public class NoOpMatchingBoundary implements MatchingBoundary {

    @Override
    public void handle(PowerOutage powerOutage) {
        // Intentionally no-op: real Matching Engine (CandidateFinder + Matching Engine) will be wired in Phase 6 (TASK 22-24).
        // TASK 16 defines only the Application boundary.
    }
}
