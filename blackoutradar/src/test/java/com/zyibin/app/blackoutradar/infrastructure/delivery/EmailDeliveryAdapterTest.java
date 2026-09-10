package com.zyibin.app.blackoutradar.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.zyibin.app.blackoutradar.application.notification.DeliveryChannelRegistry;
import com.zyibin.app.blackoutradar.application.notification.DeliveryResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryAdapterTest {

    @Mock private JavaMailSender mailSender;

    private EmailDeliveryProperties properties;
    private EmailDeliveryAdapter adapter;
    private NotificationChannel channel;

    @BeforeEach
    void setUp() {
        properties = new EmailDeliveryProperties();
        properties.setFrom("blackoutradar@localhost");
        adapter = new EmailDeliveryAdapter(mailSender, properties);
        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        channel = NotificationChannel.of(UUID.randomUUID(), user, "email", "personal@example.com", true);
    }

    @Test
    void channelTypeIsEmail() {
        assertEquals("email", adapter.channelType());
    }

    @Test
    void successfulSendUsesDestinationSenderSubjectAndMessage() {
        DeliveryResult result = adapter.deliver(channel, "outage message");

        assertTrue(result.successful());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertArrayEquals(new String[]{"personal@example.com"}, sent.getTo());
        assertEquals("blackoutradar@localhost", sent.getFrom());
        assertEquals(EmailDeliveryProperties.DEFAULT_SUBJECT, sent.getSubject());
        assertEquals("outage message", sent.getText());
    }

    @Test
    void customSubjectIsUsed() {
        properties.setSubject("Custom subject");

        adapter.deliver(channel, "outage message");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("Custom subject", captor.getValue().getSubject());
    }

    @Test
    void sendFailureReturnsFailureResult() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        DeliveryResult result = adapter.deliver(channel, "outage message");

        assertFalse(result.successful());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void nullChannelRejected() {
        assertThrows(NullPointerException.class, () -> adapter.deliver(null, "outage message"));
    }

    @Test
    void nullMessageRejected() {
        assertThrows(NullPointerException.class, () -> adapter.deliver(channel, null));
    }

    @Test
    void adapterRegistersInChannelRegistryAsEmail() {
        DeliveryChannelRegistry registry = new DeliveryChannelRegistry(List.of(adapter));

        assertTrue(registry.find("email").isPresent());
        assertEquals(adapter, registry.find("email").get());
    }
}
