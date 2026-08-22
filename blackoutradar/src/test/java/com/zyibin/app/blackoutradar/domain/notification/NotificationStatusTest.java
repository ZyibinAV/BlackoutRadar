package com.zyibin.app.blackoutradar.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class NotificationStatusTest {

    @Test
    void allApprovedValuesExist() {
        assertEquals(EnumSet.of(NotificationStatus.PENDING, NotificationStatus.PROCESSING,
                        NotificationStatus.SENT, NotificationStatus.FAILED),
                EnumSet.allOf(NotificationStatus.class));
    }
}