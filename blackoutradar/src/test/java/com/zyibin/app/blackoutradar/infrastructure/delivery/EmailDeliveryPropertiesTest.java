package com.zyibin.app.blackoutradar.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmailDeliveryPropertiesTest {

    @Test
    void defaultSubjectApplied() {
        EmailDeliveryProperties properties = new EmailDeliveryProperties();

        assertEquals(EmailDeliveryProperties.DEFAULT_SUBJECT, properties.getSubject());
        assertNull(properties.getFrom());
    }

    @Test
    void explicitValuesPreserved() {
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setFrom("sender@example.com");
        properties.setSubject("Custom subject");

        assertEquals("sender@example.com", properties.getFrom());
        assertEquals("Custom subject", properties.getSubject());
    }
}
