package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExternalIdentityDataTest {

    @Test
    void carriesProviderNeutralFields() {
        ExternalIdentityData data = new ExternalIdentityData(
                OAuth2Provider.GITHUB, "83964673", "octo@example.com", true);

        assertEquals(OAuth2Provider.GITHUB, data.provider());
        assertEquals("83964673", data.subject());
        assertEquals("octo@example.com", data.email());
        assertTrue(data.emailVerified());
        assertTrue(data.hasUsableEmail());
    }

    @Test
    void absentEmailIsAllowed() {
        ExternalIdentityData data =
                new ExternalIdentityData(OAuth2Provider.VK, "vk-sub-1", null, false);

        assertNull(data.email());
        assertFalse(data.hasUsableEmail());
    }

    @Test
    void blankEmailIsNotUsable() {
        assertFalse(new ExternalIdentityData(OAuth2Provider.VK, "vk-sub-1", "  ", false)
                .hasUsableEmail());
    }

    @Test
    void missingRequiredDataRejected() {
        assertThrows(NullPointerException.class,
                () -> new ExternalIdentityData(null, "83964673", "octo@example.com", true));
        assertThrows(NullPointerException.class,
                () -> new ExternalIdentityData(OAuth2Provider.GITHUB, null, "octo@example.com", true));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityData(OAuth2Provider.GITHUB, "  ", "octo@example.com", true));
    }
}
