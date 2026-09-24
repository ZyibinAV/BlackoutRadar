package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenPairTest {

    @Test
    void carriesBothTokens() {
        TokenPair pair = new TokenPair("access-token-value", "refresh-token-value");

        assertEquals("access-token-value", pair.accessToken());
        assertEquals("refresh-token-value", pair.refreshToken());
    }

    @Test
    void stringRepresentationHidesTokenMaterial() {
        TokenPair pair = new TokenPair("access-token-value", "refresh-token-value");

        String text = pair.toString();

        assertTrue(!text.contains("access-token-value"));
        assertTrue(!text.contains("refresh-token-value"));
    }
}
