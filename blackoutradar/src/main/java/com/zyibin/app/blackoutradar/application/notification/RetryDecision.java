package com.zyibin.app.blackoutradar.application.notification;

import java.time.Instant;
import java.util.Objects;

public record RetryDecision(boolean retryAllowed, Instant nextAttemptAt) {

    public RetryDecision {
        if (retryAllowed) {
            Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null when retry is allowed");
        } else if (nextAttemptAt != null) {
            throw new IllegalArgumentException("nextAttemptAt must be null when retry is not allowed");
        }
    }

    public static RetryDecision noRetry() {
        return new RetryDecision(false, null);
    }

    public static RetryDecision retryAt(Instant nextAttemptAt) {
        return new RetryDecision(true,
                Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null"));
    }
}
