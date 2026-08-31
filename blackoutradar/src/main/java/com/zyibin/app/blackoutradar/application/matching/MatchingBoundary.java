package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;

/**
 * Application-facing contract for future Matching subsystem.
 * Implemented in Phase 6 by CandidateFinder -> Matching Engine -> Match.
 * TASK 16 defines only the boundary; no algorithm is implemented here.
 */
public interface MatchingBoundary {

    void handle(PowerOutage powerOutage);
}
