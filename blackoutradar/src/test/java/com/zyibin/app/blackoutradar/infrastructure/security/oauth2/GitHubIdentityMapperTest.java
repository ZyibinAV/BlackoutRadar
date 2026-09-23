package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubIdentityMapperTest {

    private final GitHubIdentityMapper mapper = new GitHubIdentityMapper();

    @Test
    void mapsStableSubjectAndVerifiedPrimaryEmail() {
        Map<String, Object> attributes = Map.of(
                "id", 83964673,
                "login", "octocat",
                "emails", List.of(
                        Map.of("email", "other@example.com", "primary", false, "verified", true),
                        Map.of("email", "octo@example.com", "primary", true, "verified", true)));

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals(OAuth2Provider.GITHUB, data.provider());
        assertEquals("83964673", data.subject());
        assertEquals("octo@example.com", data.email());
        assertTrue(data.emailVerified());
        assertTrue(data.hasUsableEmail());
    }

    @Test
    void unverifiedOnlyEmailYieldsNoUsableEmail() {
        Map<String, Object> attributes = Map.of(
                "id", 83964673,
                "login", "octocat",
                "emails", List.of(
                        Map.of("email", "octo@example.com", "primary", true, "verified", false)));

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals("83964673", data.subject());
        assertNull(data.email());
        assertFalse(data.emailVerified());
        assertFalse(data.hasUsableEmail());
    }

    @Test
    void missingEmailsYieldsNoUsableEmail() {
        Map<String, Object> attributes = Map.of("id", 83964673, "login", "octocat");

        ExternalIdentityData data = mapper.map(attributes);

        assertEquals(OAuth2Provider.GITHUB, data.provider());
        assertEquals("83964673", data.subject());
        assertNull(data.email());
        assertFalse(data.hasUsableEmail());
    }

    @Test
    void loginIsNeverUsedAsIdentityKey() {
        Map<String, Object> attributes = Map.of("id", 83964673, "login", "octocat");

        assertEquals("83964673", mapper.map(attributes).subject());
    }

    @Test
    void missingStableIdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.map(Map.of("login", "octocat")));
        assertThrows(NullPointerException.class, () -> mapper.map(null));
    }
}
