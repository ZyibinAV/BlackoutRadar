package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class RecoverySchedulerTest {

    private static final Duration POLL_INTERVAL = Duration.ofMinutes(5);
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(15);

    @Mock private NotificationDeliveryPort deliveryPort;
    @Mock private NotificationDeliveryFencingPort fencingPort;
    @Mock private TaskScheduler taskScheduler;

    private RecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler,
                POLL_INTERVAL, STUCK_THRESHOLD);
    }

    private NotificationDelivery delivery() {
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
        NotificationChannel channel = NotificationChannel.of(UUID.randomUUID(), user, "email",
                "personal@example.com", true);
        return NotificationDelivery.of(UUID.randomUUID(), notification, channel);
    }

    @Test
    void stuckDeliveriesPassedToRecover() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        when(deliveryPort.findStuckDeliveries(any(Instant.class), eq(100)))
                .thenReturn(List.of(first, second));

        scheduler.recoverStuckDeliveries();

        verify(fencingPort).recoverStuck(eq(first.id()), any(Instant.class));
        verify(fencingPort).recoverStuck(eq(second.id()), any(Instant.class));
    }

    @Test
    void sameStuckBeforeUsedForAllDeliveriesInOneRun() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        when(deliveryPort.findStuckDeliveries(any(Instant.class), eq(100)))
                .thenReturn(List.of(first, second));

        scheduler.recoverStuckDeliveries();

        ArgumentCaptor<Instant> findCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(deliveryPort).findStuckDeliveries(findCaptor.capture(), eq(100));
        ArgumentCaptor<Instant> recoverCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(fencingPort).recoverStuck(eq(first.id()), recoverCaptor.capture());
        verify(fencingPort).recoverStuck(eq(second.id()), recoverCaptor.capture());
        assertEquals(findCaptor.getValue(), recoverCaptor.getAllValues().get(0));
        assertEquals(findCaptor.getValue(), recoverCaptor.getAllValues().get(1));
    }

    @Test
    void noStuckDeliveriesNothingRecovered() {
        when(deliveryPort.findStuckDeliveries(any(Instant.class), eq(100))).thenReturn(List.of());

        scheduler.recoverStuckDeliveries();

        verifyNoInteractions(fencingPort);
    }

    @Test
    void failingRecoveryDoesNotStopOthers() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        NotificationDelivery third = delivery();
        when(deliveryPort.findStuckDeliveries(any(Instant.class), eq(100)))
                .thenReturn(List.of(first, second, third));
        doThrow(new RuntimeException("recovery failed"))
                .when(fencingPort).recoverStuck(eq(first.id()), any(Instant.class));

        scheduler.recoverStuckDeliveries();

        verify(fencingPort).recoverStuck(eq(first.id()), any(Instant.class));
        verify(fencingPort).recoverStuck(eq(second.id()), any(Instant.class));
        verify(fencingPort).recoverStuck(eq(third.id()), any(Instant.class));
    }

    @Test
    void findFailureSkipsRecovery() {
        when(deliveryPort.findStuckDeliveries(any(Instant.class), eq(100)))
                .thenThrow(new RuntimeException("storage unavailable"));

        scheduler.recoverStuckDeliveries();

        verifyNoInteractions(fencingPort);
    }

    @Test
    void scheduleRegistersFixedDelayTask() {
        scheduler.schedule();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(POLL_INTERVAL));
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class,
                () -> new RecoveryScheduler(null, fencingPort, taskScheduler, POLL_INTERVAL, STUCK_THRESHOLD));
        assertThrows(NullPointerException.class,
                () -> new RecoveryScheduler(deliveryPort, null, taskScheduler, POLL_INTERVAL, STUCK_THRESHOLD));
        assertThrows(NullPointerException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, null, POLL_INTERVAL, STUCK_THRESHOLD));
        assertThrows(NullPointerException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler, null, STUCK_THRESHOLD));
        assertThrows(NullPointerException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler, POLL_INTERVAL, null));
    }

    @Test
    void nonPositivePollIntervalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler,
                        Duration.ZERO, STUCK_THRESHOLD));
        assertThrows(IllegalArgumentException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler,
                        Duration.ofSeconds(-5), STUCK_THRESHOLD));
    }

    @Test
    void nonPositiveStuckThresholdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler,
                        POLL_INTERVAL, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RecoveryScheduler(deliveryPort, fencingPort, taskScheduler,
                        POLL_INTERVAL, Duration.ofSeconds(-5)));
    }

    @Test
    void schedulerDependsOnlyOnPortsAndScheduling() {
        Set<String> allowed = Set.of(
                "com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort",
                "com.zyibin.app.blackoutradar.application.notification.NotificationDeliveryFencingPort",
                "org.springframework.scheduling.TaskScheduler",
                "java.time.Duration",
                "org.slf4j.Logger",
                "int");
        Stream.of(RecoveryScheduler.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .forEach(typeName -> assertTrue(allowed.contains(typeName),
                        "Scheduler must not depend on " + typeName));
        Set<String> fieldTypes = Stream.of(RecoveryScheduler.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(fieldTypes.contains("com.zyibin.app.blackoutradar.application.notification.RetryPolicy"),
                "Scheduler must not depend on RetryPolicy");
        assertFalse(fieldTypes.contains("com.zyibin.app.blackoutradar.application.notification.DeliveryPort"),
                "Scheduler must not depend on DeliveryPort");
        assertFalse(fieldTypes.contains(
                "com.zyibin.app.blackoutradar.application.notification.DeliveryChannelRegistry"),
                "Scheduler must not depend on DeliveryChannelRegistry");
        verifyNoInteractions(deliveryPort, fencingPort, taskScheduler);
    }

    @Test
    void schedulerContainsNoChannelSpecificLogic() {
        Stream.of(RecoveryScheduler.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .forEach(name -> {
                    assertFalse(name.contains("email"), "Scheduler must not contain " + name);
                    assertFalse(name.contains("telegram"), "Scheduler must not contain " + name);
                    assertFalse(name.contains("smtp"), "Scheduler must not contain " + name);
                    assertFalse(name.contains("sms"), "Scheduler must not contain " + name);
                });
        verifyNoInteractions(deliveryPort, fencingPort, taskScheduler);
    }

    @Test
    void recoverUsesFencingPortNotDirectSave() {
        Stream.of(RecoveryScheduler.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .forEach(name -> assertFalse(name.contains("save"),
                        "Scheduler must not save directly: " + name));
        verify(fencingPort, never()).recoverStuck(any(UUID.class), any(Instant.class));
        verifyNoInteractions(deliveryPort, fencingPort, taskScheduler);
    }
}
