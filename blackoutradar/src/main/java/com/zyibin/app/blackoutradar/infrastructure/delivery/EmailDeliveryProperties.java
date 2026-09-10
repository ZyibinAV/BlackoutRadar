package com.zyibin.app.blackoutradar.infrastructure.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blackoutradar.notification.email")
public class EmailDeliveryProperties {

    public static final String DEFAULT_SUBJECT = "BlackoutRadar power outage notification";

    private String from;
    private String subject = DEFAULT_SUBJECT;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
