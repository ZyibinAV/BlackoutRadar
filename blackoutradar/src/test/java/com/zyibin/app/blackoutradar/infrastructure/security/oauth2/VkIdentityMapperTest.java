package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VkIdentityMapperTest {

    private final VkIdentityMapper mapper = new VkIdentityMapper();

    @Test
    void mapsStableSubjectAndEmail() {
        Map<String, Object> attributes = Map.of(
                "sub", "vk-user-42",
                "email", "vk@example.com",
                "email_verified", true);

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals(OAuth2Provider.VK, data.provider());
        assertEquals("vk-user-42", data.subject());
        assertEquals("vk@example.com", data.email());
        assertTrue(data.emailVerified());
        assertTrue(data.hasUsableEmail());
    }

    @Test
    void missingEmailIsAllowed() {
        Map<String, Object> attributes = Map.of("sub", "vk-user-42");

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals(OAuth2Provider.VK, data.provider());
        assertEquals("vk-user-42", data.subject());
        assertNull(data.email());
        assertFalse(data.emailVerified());
        assertFalse(data.hasUsableEmail());
    }

    @Test
    void unverifiedEmailFlagIsCarried() {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("sub", "vk-user-42");
        attributes.put("email", "vk@example.com");
        attributes.put("email_verified", false);

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals("vk@example.com", data.email());
        assertFalse(data.emailVerified());
    }

    @Test
    void missingStableIdentifierRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.map(Map.of("email", "vk@example.com")));
        assertThrows(NullPointerException.class, () -> mapper.map(null));
    }
}
