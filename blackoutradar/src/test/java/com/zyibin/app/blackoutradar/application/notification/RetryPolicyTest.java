package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final RetryPolicy policy = new FixedRetryPolicy();

    @Test
    void successNeverRetries() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            RetryDecision decision = policy.decide(DeliveryAttemptResult.SUCCESS, attempt, NOW);

            assertFalse(decision.retryAllowed());
            assertNull(decision.nextAttemptAt());
        }
    }

    @Test
    void permanentFailureNeverRetries() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            RetryDecision decision = policy.decide(DeliveryAttemptResult.PERMANENT_FAILURE, attempt, NOW);

            assertFalse(decision.retryAllowed());
            assertNull(decision.nextAttemptAt());
        }
    }

    @Test
    void temporaryFailureAfterFirstAttemptRetriesInOneMinute() {
        RetryDecision decision = policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, 1, NOW);

        assertTrue(decision.retryAllowed());
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), decision.nextAttemptAt());
    }

    @Test
    void temporaryFailureAfterSecondAttemptRetriesInFiveMinutes() {
        RetryDecision decision = policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, 2, NOW);

        assertTrue(decision.retryAllowed());
        assertEquals(Instant.parse("2026-01-01T00:05:00Z"), decision.nextAttemptAt());
    }

    @Test
    void temporaryFailureAfterThirdAttemptForbidsRetry() {
        RetryDecision decision = policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, 3, NOW);

        assertFalse(decision.retryAllowed());
        assertNull(decision.nextAttemptAt());
    }

    @Test
    void invalidAttemptNumberRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, 0, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, -1, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> policy.decide(DeliveryAttemptResult.SUCCESS, 0, NOW));
    }

    @Test
    void nullResultRejected() {
        assertThrows(NullPointerException.class, () -> policy.decide(null, 1, NOW));
    }

    @Test
    void nullNowRejected() {
        assertThrows(NullPointerException.class,
                () -> policy.decide(DeliveryAttemptResult.TEMPORARY_FAILURE, 1, null));
    }

    @Test
    void decisionModelInvariants() {
        assertFalse(RetryDecision.noRetry().retryAllowed());
        assertNull(RetryDecision.noRetry().nextAttemptAt());
        assertTrue(RetryDecision.retryAt(NOW).retryAllowed());
        assertEquals(NOW, RetryDecision.retryAt(NOW).nextAttemptAt());
        assertThrows(NullPointerException.class, () -> RetryDecision.retryAt(null));
        assertThrows(IllegalArgumentException.class, () -> new RetryDecision(false, NOW));
    }
}
