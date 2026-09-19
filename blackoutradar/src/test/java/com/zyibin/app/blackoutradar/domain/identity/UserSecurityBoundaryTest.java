package com.zyibin.app.blackoutradar.domain.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

class UserSecurityBoundaryTest {

    @Test
    void userHoldsNoCredentialState() {
        Set<String> fields = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("password")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("credential")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("token")));
        assertTrue(fields.stream().noneMatch(name -> name.toLowerCase().contains("secret")));
    }

    @Test
    void userIsNotASpringSecurityPrincipal() {
        assertFalse(UserDetails.class.isAssignableFrom(User.class));
    }

    @Test
    void userExposesNoSecurityFrameworkTypes() {
        for (Field field : User.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("org.springframework"),
                    "Domain User must not depend on " + field.getType().getName());
        }
    }
}
