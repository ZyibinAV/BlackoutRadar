package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Technical fencing contract between Application Retry Processing and Persistence.
 * Ownership token is a UUID concurrency mechanism, not a business property
 * of NotificationDelivery. All state changes after claim are conditional
 * on the token inside a single atomic SQL UPDATE (no SELECT-then-UPDATE,
 * no JVM locks, no long DB locks).
 */
public interface NotificationDeliveryFencingPort {

    /**
     * Atomically transitions READY (due) to PROCESSING and assigns
     * a fresh ownership token. Only one concurrent claimant succeeds.
     */
    Optional<DeliveryClaim> claim(UUID deliveryId, Instant now);

    /**
     * Atomically persists the computed end state (SENT, FAILED or READY for retry)
     * only when the caller still owns the delivery:
     * status is PROCESSING and stored token equals the given token.
     * On success ownership is cleared. Returns empty when fencing failed
     * (0 rows affected) without changing any state.
     */
    Optional<NotificationDelivery> saveIfOwned(UUID deliveryId, UUID ownershipToken,
                                               NotificationDelivery newState);

    /**
     * Atomically recovers a stuck delivery: PROCESSING with an incomplete
     * attempt started strictly before the threshold goes to READY
     * with cleared ownership. Attempt history is untouched.
     * Returns true only when one row was affected.
     */
    boolean recoverStuck(UUID deliveryId, Instant stuckBefore);
}
