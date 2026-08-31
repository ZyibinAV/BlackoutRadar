package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParsedOutageTest {

    private final Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
    private final AddressInput addr1 = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
    private final AddressInput addr2 = new AddressInput("Омская область", null, null, "Омск", null, "ул Мира", "10");

    @Test
    void creationWithAllFields() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T05:00:00Z");
        ParsedOutage po = new ParsedOutage(source, start, end, "Аварийное", "ext-123", List.of(addr1, addr2));
        assertEquals(source, po.source());
        assertEquals(start, po.startTime());
        assertEquals(end, po.endTime());
        assertEquals("Аварийное", po.reason());
        assertEquals("ext-123", po.externalReference());
        assertEquals(2, po.addresses().size());
    }

    @Test
    void externalReferenceCanBeNull() {
        ParsedOutage po = new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(addr1));
        assertNull(po.externalReference());
    }

    @Test
    void externalReferenceBlankRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "r", "   ", List.of(addr1)));
    }

    @Test
    void rejectsEmptyAddresses() {
        assertThrows(IllegalArgumentException.class, () ->
                new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of()));
    }

    @Test
    void rejectsNullAddressInList() {
        assertThrows(NullPointerException.class, () ->
                new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(addr1, null)));
    }

    @Test
    void rejectsBlankReason() {
        assertThrows(IllegalArgumentException.class, () ->
                new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "   ", null, List.of(addr1)));
    }

    @Test
    void rejectsInvalidTimeRange() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () ->
                new ParsedOutage(source, now, now, "r", null, List.of(addr1)));
        assertThrows(IllegalArgumentException.class, () ->
                new ParsedOutage(source, now.plusSeconds(3600), now, "r", null, List.of(addr1)));
    }

    @Test
    void addressesAreImmutable() {
        ParsedOutage po = new ParsedOutage(source, Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(addr1));
        assertThrows(UnsupportedOperationException.class, () -> po.addresses().add(addr2));
    }
}
