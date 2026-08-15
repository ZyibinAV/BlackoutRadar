package com.zyibin.app.blackoutradar.domain.common;

public final class DomainPreconditions {

    private DomainPreconditions() {
    }

    public static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}