package com.zyibin.app.blackoutradar.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void allApprovedValuesExist() {
        assertEquals(EnumSet.of(UserRole.USER, UserRole.ADMIN), EnumSet.allOf(UserRole.class));
    }
}