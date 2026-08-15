package com.zyibin.app.blackoutradar.domain.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PowerOutageAddressTest {

    @Test
    void validPowerOutageAddress() {
        PowerOutage outage = OutageTestData.outage();
        Address address = OutageTestData.address();
        PowerOutageAddress poa = PowerOutageAddress.of(UUID.randomUUID(), outage, address);

        assertSame(outage, poa.powerOutage());
        assertSame(address, poa.address());
        assertNull(poa.transformerStation());
    }

    @Test
    void validPowerOutageAddressWithTransformerStation() {
        PowerOutage outage = OutageTestData.outage();
        TransformerStation station = TransformerStation.of(UUID.randomUUID(), "ТП-1");
        PowerOutageAddress poa = PowerOutageAddress.of(UUID.randomUUID(), outage,
                OutageTestData.address(), station);

        assertSame(station, poa.transformerStation());
    }

    @Test
    void nullPowerOutageRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutageAddress.of(UUID.randomUUID(), null, OutageTestData.address()));
    }

    @Test
    void nullAddressRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutageAddress.of(UUID.randomUUID(), OutageTestData.outage(), null));
    }

    @Test
    void nullIdRejected() {
        assertThrows(NullPointerException.class,
                () -> PowerOutageAddress.of(null, OutageTestData.outage(), OutageTestData.address()));
    }

    @Test
    void equalityById() {
        UUID id = UUID.randomUUID();
        PowerOutage outage = OutageTestData.outage();
        Address address = OutageTestData.address();
        PowerOutageAddress a = PowerOutageAddress.of(id, outage, address);
        PowerOutageAddress b = PowerOutageAddress.of(id, outage, address);

        assertNotEquals(a, PowerOutageAddress.of(UUID.randomUUID(), outage, address));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void samePowerOutageAndAddressCannotBeRepresentedTwice() {
        PowerOutage outage = OutageTestData.outage();
        Address address = OutageTestData.address();
        PowerOutageAddress first = PowerOutageAddress.of(UUID.randomUUID(), outage, address);
        PowerOutageAddress second = PowerOutageAddress.of(UUID.randomUUID(), outage, address);

        assertThrows(IllegalArgumentException.class,
                () -> PowerOutage.of(UUID.randomUUID(), OutageTestData.source(),
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T05:00:00Z"),
                        "Аварийное отключение", "АКТИВНО", List.of(first, second)));
    }
}