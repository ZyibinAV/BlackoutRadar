package com.zyibin.app.blackoutradar.application.notification;

import java.util.Objects;

public record DeliveryResult(DeliveryOutcome outcome) {

    public DeliveryResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public static DeliveryResult success() {
        return new DeliveryResult(DeliveryOutcome.SUCCESS);
    }

    public static DeliveryResult temporaryFailure() {
        return new DeliveryResult(DeliveryOutcome.TEMPORARY_FAILURE);
    }

    public static DeliveryResult permanentFailure() {
        return new DeliveryResult(DeliveryOutcome.PERMANENT_FAILURE);
    }

    public boolean successful() {
        return outcome == DeliveryOutcome.SUCCESS;
    }
}
