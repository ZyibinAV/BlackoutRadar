package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageFactory {

    public String createMessage(PowerOutage powerOutage) {
        Objects.requireNonNull(powerOutage, "powerOutage must not be null");
        return "Power outage: " + powerOutage.startTime()
                + " - " + powerOutage.endTime()
                + ". Reason: " + powerOutage.reason();
    }
}
