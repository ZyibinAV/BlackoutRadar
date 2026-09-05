package com.zyibin.app.blackoutradar.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationMessageFactoryTest {

    private final NotificationMessageFactory factory = new NotificationMessageFactory();

    private PowerOutage outage(Instant start, Instant end, String reason) {
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        Address address = Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
        return PowerOutage.of(UUID.randomUUID(), source, start, end, reason, "АКТИВНО",
                List.of(PowerOutageAddress.unboundOf(UUID.randomUUID(), address)));
    }

    @Test
    void createsMessageInExactFormat() {
        PowerOutage outage = outage(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T02:00:00Z"),
                "Аварийное отключение");

        assertEquals("Power outage: 2026-01-01T00:00:00Z - 2026-01-01T02:00:00Z. Reason: Аварийное отключение",
                factory.createMessage(outage));
    }

    @Test
    void messageReflectsOutageData() {
        PowerOutage outage = outage(
                Instant.parse("2026-03-10T10:15:00Z"),
                Instant.parse("2026-03-10T12:45:00Z"),
                "Плановые работы");

        assertEquals("Power outage: 2026-03-10T10:15:00Z - 2026-03-10T12:45:00Z. Reason: Плановые работы",
                factory.createMessage(outage));
    }

    @Test
    void nullPowerOutageRejected() {
        assertThrows(NullPointerException.class, () -> factory.createMessage(null));
    }
}
