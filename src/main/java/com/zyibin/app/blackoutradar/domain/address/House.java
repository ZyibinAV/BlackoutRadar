package com.zyibin.app.blackoutradar.domain.address;

import com.zyibin.app.blackoutradar.domain.common.DomainPreconditions;
import java.util.Objects;

public record House(String houseNumber, String houseAddition, String canonicalHouse) {

    public House {
        houseNumber = Objects.requireNonNull(houseNumber, "houseNumber must not be null");
        houseNumber = DomainPreconditions.requireNotBlank(houseNumber, "houseNumber must not be blank");
        canonicalHouse = Objects.requireNonNull(canonicalHouse, "canonicalHouse must not be null");
        canonicalHouse = DomainPreconditions.requireNotBlank(canonicalHouse, "canonicalHouse must not be blank");
    }
}