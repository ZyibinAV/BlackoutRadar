package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.DeliveryStatus;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.NotificationStatus;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryCompletionServiceTest {

    @Mock private NotificationDeliveryFencingPort fencingPort;
    @Mock private NotificationFinalizationService finalizationService;

    private DeliveryCompletionService service;

    private NotificationDelivery delivery;

    @BeforeEach
    void setUp() {
        service = new DeliveryCompletionService(fencingPort, finalizationService);

        User user = User.of(UUID.randomUUID(), "user@example.com", UserRole.USER, true);
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        Region region = Region.of(UUID.randomUUID(), "ОМСКАЯ ОБЛАСТЬ");
        City city = City.of(UUID.randomUUID(), region, "ОМСК");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "ЛЕНИНА");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        PowerOutage powerOutage = PowerOutage.of(UUID.randomUUID(), source, start, end, "reason",
                "АКТИВНО", List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
        Subscription subscription = Subscription.of(UUID.randomUUID(), user, address, start, end,
                true, end.plusSeconds(3600));
        Notification notification = Notification.of(UUID.randomUUID(), subscription, powerOutage, "message");
        NotificationChannel channel = NotificationChannel.of(UUID.randomUUID(), user,
                "email", "personal@example.com", true);
        delivery = NotificationDelivery.of(UUID.randomUUID(), notification, channel);
    }

    @Test
    void successfulFencingTriggersFinalizationInOrder() {
        UUID token = UUID.randomUUID();
        NotificationDelivery sent = delivery.startProcessing().markSent();
        Notification finalized = delivery.notification().startProcessing().markSent();
        when(fencingPort.saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class)))
                .thenReturn(Optional.of(sent));
        when(finalizationService.finalizeNotification(delivery.notification().id()))
                .thenReturn(finalized);

        Optional<Notification> result = service.complete(delivery.id(), token, sent);

        assertTrue(result.isPresent());
        assertEquals(NotificationStatus.SENT, result.get().status());
        InOrder inOrder = inOrder(fencingPort, finalizationService);
        inOrder.verify(fencingPort).saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class));
        inOrder.verify(finalizationService).finalizeNotification(delivery.notification().id());
    }

    @Test
    void lostOwnershipSkipsFinalization() {
        UUID token = UUID.randomUUID();
        NotificationDelivery sent = delivery.startProcessing().markSent();
        when(fencingPort.saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class)))
                .thenReturn(Optional.empty());

        Optional<Notification> result = service.complete(delivery.id(), token, sent);

        assertFalse(result.isPresent());
        verify(fencingPort).saveIfOwned(eq(delivery.id()), eq(token), any(NotificationDelivery.class));
        verifyNoInteractions(finalizationService);
    }

    @Test
    void nullArgumentsRejected() {
        NotificationDelivery sent = delivery.startProcessing().markSent();
        UUID token = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> service.complete(null, token, sent));
        assertThrows(NullPointerException.class, () -> service.complete(delivery.id(), null, sent));
        assertThrows(NullPointerException.class, () -> service.complete(delivery.id(), token, null));
        verifyNoInteractions(fencingPort, finalizationService);
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class,
                () -> new DeliveryCompletionService(null, finalizationService));
        assertThrows(NullPointerException.class,
                () -> new DeliveryCompletionService(fencingPort, null));
    }
}
