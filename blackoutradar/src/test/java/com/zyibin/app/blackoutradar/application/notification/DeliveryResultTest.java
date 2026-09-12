package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeliveryResultTest {

    @Test
    void successResult() {
        DeliveryResult result = DeliveryResult.success();

        assertEquals(DeliveryOutcome.SUCCESS, result.outcome());
        assertTrue(result.successful());
    }

    @Test
    void temporaryFailureResult() {
        DeliveryResult result = DeliveryResult.temporaryFailure();

        assertEquals(DeliveryOutcome.TEMPORARY_FAILURE, result.outcome());
        assertFalse(result.successful());
    }

    @Test
    void permanentFailureResult() {
        DeliveryResult result = DeliveryResult.permanentFailure();

        assertEquals(DeliveryOutcome.PERMANENT_FAILURE, result.outcome());
        assertFalse(result.successful());
    }

    @Test
    void nullOutcomeRejected() {
        assertThrows(NullPointerException.class, () -> new DeliveryResult(null));
    }
}
