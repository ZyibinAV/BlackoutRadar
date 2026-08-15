package com.zyibin.app.blackoutradar.domain.outage;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OutageTestData {

    private OutageTestData() {
    }

    public static Source source() {
        return Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);
    }

    public static Address address() {
        Region region = Region.of(UUID.randomUUID(), "Омская область");
        City city = City.of(UUID.randomUUID(), region, "Омск");
        Street street = Street.of(UUID.randomUUID(), city, StreetType.STREET, "Ленина");
        return Address.of(UUID.randomUUID(), street, new House("15", null, "15"));
    }

    public static PowerOutage outage() {
        PowerOutageAddress poa = PowerOutageAddress.unboundOf(UUID.randomUUID(), address());
        return PowerOutage.of(UUID.randomUUID(), source(), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T05:00:00Z"), "Аварийное отключение", "АКТИВНО",
                List.of(poa));
    }
}