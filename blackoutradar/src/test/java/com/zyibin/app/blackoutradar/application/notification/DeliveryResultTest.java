package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeliveryResultTest {

    @Test
    void successResult() {
        assertTrue(DeliveryResult.success().successful());
    }

    @Test
    void failureResult() {
        assertFalse(DeliveryResult.failure().successful());
    }
}
