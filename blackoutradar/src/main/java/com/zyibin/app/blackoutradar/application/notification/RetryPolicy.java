package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.DeliveryAttemptResult;
import java.time.Instant;

public interface RetryPolicy {

    RetryDecision decide(DeliveryAttemptResult result, int attemptNumber, Instant now);
}
