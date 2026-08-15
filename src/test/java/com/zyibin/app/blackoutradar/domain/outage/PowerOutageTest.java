package com.zyibin.app.blackoutradar.domain.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PowerOutageTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T05:00:00Z");

    private static PowerOutageAddress addressEntry() {
        return PowerOutageAddress.unboundOf(UUID.randomUUID(), OutageTestData.address());
    }

    @Test
    void validPowerOutage() {
        PowerOutageAddress poa = addressEntry();
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", List.of(poa));

        assertEquals("Аварийное отключение", outage.reason());
        assertEquals("АКТИВНО", outage.status());
        assertEquals(START, outage.startTime());
        assertEquals(END, outage.endTime());
        assertEquals(1, outage.addresses().size());
        assertSame(outage, outage.addresses().iterator().next().powerOutage());
    }

    @Test
    void nullSourceRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutage.of(UUID.randomUUID(), null, START, END,
                        "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void nullStartTimeRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), null, END,
                        "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void nullEndTimeRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, null,
                        "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void nullReasonRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END, null,
                        "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void blankReasonRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END, "   ",
                        "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void nullStatusRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", null, List.of(addressEntry())));
    }

    @Test
    void blankStatusRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", "  ", List.of(addressEntry())));
    }

    @Test
    void equalStartAndEndRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, START,
                        "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void startAfterEndRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), END, START,
                        "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
    }

    @Test
    void nullAddressesRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", "АКТИВНО", null));
    }

    @Test
    void emptyAddressesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", "АКТИВНО", List.of()));
    }

    @Test
    void nullElementInAddressesRejected() {
        List<PowerOutageAddress> addresses = new ArrayList<>();
        addresses.add(addressEntry());
        addresses.add(null);

        assertThrows(NullPointerException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", "АКТИВНО", addresses));
    }

    @Test
    void duplicateAddressRejected() {
        com.zyibin.app.blackoutradar.domain.address.Address same = OutageTestData.address();
        PowerOutageAddress first = PowerOutageAddress.unboundOf(UUID.randomUUID(), same);
        PowerOutageAddress second = PowerOutageAddress.unboundOf(UUID.randomUUID(), same);

        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                        "Аварийное отключение", "АКТИВНО", List.of(first, second)));
    }

    @Test
    void addressesCollectionIsImmutable() {
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", List.of(addressEntry()));

        assertThrows(UnsupportedOperationException.class, () -> outage.addresses().clear());
    }

    @Test
    void externalCollectionChangesDoNotAffectAggregate() {
        List<PowerOutageAddress> source = new ArrayList<>();
        source.add(addressEntry());
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", source);
        source.clear();

        assertEquals(1, outage.addresses().size());
    }

    @Test
    void historicalStateNotDeleted() {
        PowerOutage outage = PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                "Аварийное отключение", "ЗАВЕРШЕНО", List.of(addressEntry()));

        assertEquals("Аварийное отключение", outage.reason());
        assertEquals("ЗАВЕРШЕНО", outage.status());
        assertEquals(START, outage.startTime());
        assertEquals(END, outage.endTime());
        assertEquals(1, outage.addresses().size());
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        PowerOutage a = PowerOutage.of(id, OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", List.of(addressEntry()));
        PowerOutage b = PowerOutage.of(id, OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", List.of(addressEntry()));

        assertNotEquals(a, PowerOutage.of(UUID.randomUUID(), OutageTestData.source(), START, END,
                "Аварийное отключение", "АКТИВНО", List.of(addressEntry())));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}