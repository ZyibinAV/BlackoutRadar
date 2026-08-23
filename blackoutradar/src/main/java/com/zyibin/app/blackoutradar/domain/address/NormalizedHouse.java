package com.zyibin.app.blackoutradar.domain.address;

import java.util.Objects;

public record NormalizedHouse(String houseNumber, String houseAddition, String canonicalHouse) {
    public NormalizedHouse {
        Objects.requireNonNull(houseNumber, "houseNumber must not be null");
        Objects.requireNonNull(canonicalHouse, "canonicalHouse must not be null");
        if (houseNumber.isBlank()) {
            throw new IllegalArgumentException("houseNumber must not be blank");
        }
        if (canonicalHouse.isBlank()) {
            throw new IllegalArgumentException("canonicalHouse must not be blank");
        }
    }
}
