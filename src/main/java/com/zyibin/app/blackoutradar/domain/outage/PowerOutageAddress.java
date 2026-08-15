package com.zyibin.app.blackoutradar.domain.outage;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import java.util.Objects;
import java.util.UUID;

public final class PowerOutageAddress {

    private final UUID id;
    private final PowerOutage powerOutage;
    private final Address address;
    private final TransformerStation transformerStation;

    private PowerOutageAddress(UUID id, PowerOutage powerOutage, Address address,
                               TransformerStation transformerStation) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.powerOutage = powerOutage;
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.transformerStation = transformerStation;
    }

    public static PowerOutageAddress of(UUID id, PowerOutage powerOutage, Address address) {
        return new PowerOutageAddress(id,
                Objects.requireNonNull(powerOutage, "powerOutage must not be null"), address, null);
    }

    public static PowerOutageAddress of(UUID id, PowerOutage powerOutage, Address address,
                                        TransformerStation transformerStation) {
        return new PowerOutageAddress(id,
                Objects.requireNonNull(powerOutage, "powerOutage must not be null"), address,
                transformerStation);
    }

    static PowerOutageAddress unboundOf(UUID id, Address address) {
        return new PowerOutageAddress(id, null, address, null);
    }

    static PowerOutageAddress unboundOf(UUID id, Address address,
                                        TransformerStation transformerStation) {
        return new PowerOutageAddress(id, null, address, transformerStation);
    }

    PowerOutageAddress withPowerOutage(PowerOutage owner) {
        return new PowerOutageAddress(id, owner, address, transformerStation);
    }

    public UUID id() {
        return id;
    }

    public PowerOutage powerOutage() {
        return powerOutage;
    }

    public Address address() {
        return address;
    }

    public TransformerStation transformerStation() {
        return transformerStation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PowerOutageAddress that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PowerOutageAddress{"
                + "id=" + id
                + ", powerOutage=" + powerOutage
                + ", address=" + address
                + ", transformerStation=" + transformerStation
                + '}';
    }
}