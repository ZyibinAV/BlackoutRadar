package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FixedRetryPolicy implements RetryPolicy {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration MAX_DELAY = Duration.ofMinutes(15);
    private static final List<Duration> BACKOFFS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));

    @Override
    public RetryDecision decide(DeliveryAttemptResult result, int attemptNumber, Instant now) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        if (result != DeliveryAttemptResult.TEMPORARY_FAILURE) {
            return RetryDecision.noRetry();
        }
        if (attemptNumber >= MAX_ATTEMPTS) {
            return RetryDecision.noRetry();
        }
        Duration delay = BACKOFFS.get(attemptNumber - 1);
        if (delay.compareTo(MAX_DELAY) > 0) {
            delay = MAX_DELAY;
        }
        return RetryDecision.retryAt(now.plus(delay));
    }
}
