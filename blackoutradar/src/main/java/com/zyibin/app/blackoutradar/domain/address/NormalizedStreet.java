package com.zyibin.app.blackoutradar.domain.address;

import java.util.Objects;

public record NormalizedStreet(StreetType type, String canonicalName) {
    public NormalizedStreet {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        if (canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName must not be blank");
        }
    }
}
