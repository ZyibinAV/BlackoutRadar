package com.zyibin.app.blackoutradar.infrastructure.delivery;

import com.zyibin.app.blackoutradar.application.notification.DeliveryPort;
import com.zyibin.app.blackoutradar.application.notification.DeliveryResult;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryAdapter implements DeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryAdapter.class);

    private final JavaMailSender mailSender;
    private final EmailDeliveryProperties properties;

    public EmailDeliveryAdapter(JavaMailSender mailSender, EmailDeliveryProperties properties) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String channelType() {
        return "email";
    }

    @Override
    public DeliveryResult deliver(NotificationChannel channel, String message) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(message, "message must not be null");
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(channel.destination());
            mail.setFrom(properties.getFrom());
            mail.setSubject(properties.getSubject());
            mail.setText(message);
            mailSender.send(mail);
            return DeliveryResult.success();
        } catch (MailException e) {
            log.warn("Email delivery failed for destination {}", channel.destination(), e);
            return DeliveryResult.failure();
        }
    }
}
