package com.zyibin.app.blackoutradar.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.zyibin.app.blackoutradar.application.notification.RetryProcessingService;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    @Mock private NotificationDeliveryPort deliveryPort;
    @Mock private RetryProcessingService processingService;
    @Mock private TaskScheduler taskScheduler;

    private RetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RetryScheduler(deliveryPort, processingService, taskScheduler, POLL_INTERVAL);
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
    void dueDeliveriesPassedToProcessingService() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        when(deliveryPort.findReadyForProcessing(any(Instant.class), eq(100)))
                .thenReturn(List.of(first, second));

        scheduler.processDueDeliveries();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(processingService).process(eq(first.id()), instantCaptor.capture());
        verify(processingService).process(eq(second.id()), instantCaptor.capture());
        assertEquals(instantCaptor.getAllValues().get(0), instantCaptor.getAllValues().get(1));
    }

    @Test
    void noDueDeliveriesNothingProcessed() {
        when(deliveryPort.findReadyForProcessing(any(Instant.class), eq(100))).thenReturn(List.of());

        scheduler.processDueDeliveries();

        verifyNoInteractions(processingService);
    }

    @Test
    void failingDeliveryDoesNotStopOthers() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        when(deliveryPort.findReadyForProcessing(any(Instant.class), eq(100)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("processing failed"))
                .when(processingService).process(eq(first.id()), any(Instant.class));

        scheduler.processDueDeliveries();

        verify(processingService).process(eq(first.id()), any(Instant.class));
        verify(processingService).process(eq(second.id()), any(Instant.class));
    }

    @Test
    void findFailureSkipsProcessing() {
        when(deliveryPort.findReadyForProcessing(any(Instant.class), eq(100)))
                .thenThrow(new RuntimeException("storage unavailable"));

        scheduler.processDueDeliveries();

        verifyNoInteractions(processingService);
    }

    @Test
    void scheduleRegistersFixedDelayTask() {
        scheduler.schedule();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(POLL_INTERVAL));
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class,
                () -> new RetryScheduler(null, processingService, taskScheduler, POLL_INTERVAL));
        assertThrows(NullPointerException.class,
                () -> new RetryScheduler(deliveryPort, null, taskScheduler, POLL_INTERVAL));
        assertThrows(NullPointerException.class,
                () -> new RetryScheduler(deliveryPort, processingService, null, POLL_INTERVAL));
        assertThrows(NullPointerException.class,
                () -> new RetryScheduler(deliveryPort, processingService, taskScheduler, null));
    }

    @Test
    void nonPositiveIntervalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryScheduler(deliveryPort, processingService, taskScheduler, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryScheduler(deliveryPort, processingService, taskScheduler,
                        Duration.ofSeconds(-5)));
    }

    @Test
    void schedulerDependsOnlyOnPortProcessingServiceAndScheduling() {
        Set<String> allowed = Set.of(
                "com.zyibin.app.blackoutradar.domain.notification.port.NotificationDeliveryPort",
                "com.zyibin.app.blackoutradar.application.notification.RetryProcessingService",
                "org.springframework.scheduling.TaskScheduler",
                "java.time.Duration",
                "org.slf4j.Logger",
                "int");
        Stream.of(RetryScheduler.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .forEach(typeName -> assertTrue(allowed.contains(typeName),
                        "Scheduler must not depend on " + typeName));
        verifyNoInteractions(deliveryPort, processingService, taskScheduler);
    }
}
