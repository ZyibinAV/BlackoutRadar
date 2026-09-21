package com.zyibin.app.blackoutradar.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Security boundary verification for TASK 30: the business domain model
 * carries no JWT, token or rotation state, and the security identity carries
 * no credentials after authentication.
 */
class SecurityTokenBoundaryTest {

    @Test
    void domainUserCarriesNoTokenState() {
        Set<String> fields = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("token")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("jwt")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("hash")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("family")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("revok")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("expir")));
    }

    @Test
    void domainHasNoTokenTypes() {
        assertTrue(isAbsent("com.zyibin.app.blackoutradar.domain.identity.RefreshToken"));
        assertTrue(isAbsent("com.zyibin.app.blackoutradar.domain.identity.AccessToken"));
        assertTrue(isAbsent("com.zyibin.app.blackoutradar.domain.identity.JwtToken"));
    }

    @Test
    void domainExposesNoFrameworkTypes() {
        for (Field field : User.class.getDeclaredFields()) {
            assertTrue(!field.getType().getName().startsWith("org.springframework"),
                    "Domain User must not depend on " + field.getType().getName());
            assertTrue(!field.getType().getName().startsWith("jakarta.persistence"),
                    "Domain User must not depend on " + field.getType().getName());
        }
    }

    @Test
    void securityIdentityCarriesNoCredentials() {
        AuthenticatedUser identity = new AuthenticatedUser(
                UUID.randomUUID(), "user@example.com", UserRole.USER, true);

        assertNull(identity.getPassword());
        Set<String> fields = Arrays.stream(AuthenticatedUser.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("password")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("hash")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("token")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("secret")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("credential")));
    }

    @Test
    void refreshPersistenceStaysOutsideDomain() {
        assertEquals("com.zyibin.app.blackoutradar.persistence.jpa.entity",
                com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity.class
                        .getPackageName());
    }

    private boolean isAbsent(String className) {
        try {
            Class.forName(className);
            return false;
        } catch (ClassNotFoundException expected) {
            return true;
        }
    }
}
