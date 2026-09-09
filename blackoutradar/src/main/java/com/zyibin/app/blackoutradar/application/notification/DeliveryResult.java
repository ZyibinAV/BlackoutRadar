package com.zyibin.app.blackoutradar.application.notification;

public record DeliveryResult(boolean successful) {

    public static DeliveryResult success() {
        return new DeliveryResult(true);
    }

    public static DeliveryResult failure() {
        return new DeliveryResult(false);
    }
}
