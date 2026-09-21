package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Unit tests for JWT access token issuance and validation.
 *
 * <p>No Spring context, no database: the service is constructed directly with
 * generated RSA keys.
 */
class JwtServiceTest {

    private static final String ISSUER = "blackoutradar-test";
    private static final String AUDIENCE = "blackoutradar-test-api";

    private KeyPair keyPair;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        jwtService = new JwtService(testProperties(keyPair, ISSUER, AUDIENCE,
                Duration.ofMinutes(15)));
    }

    @Test
    void issuesTokenWithRequiredClaims() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);

        Jwt jwt = jwtService.decode(token);
        assertEquals(ISSUER, jwt.getClaims().get("iss"));
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals(List.of(AUDIENCE), jwt.getAudience());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertNotNull(jwt.getId());
    }

    @Test
    void tokenContainsOnlyExpectedClaims() {
        String token = jwtService.generateAccessToken(UUID.randomUUID());

        Jwt jwt = jwtService.decode(token);
        assertEquals(Set.of("iss", "sub", "aud", "iat", "exp", "jti"), jwt.getClaims().keySet());
    }

    @Test
    void subjectIsUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);

        assertEquals(userId, jwtService.parseUserId(token));
    }

    @Test
    void tokenCarriesNoSecretsOrDomainState() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId);

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
        assertTrue(!payload.contains("password"));
        assertTrue(!payload.contains("credential"));
        assertTrue(!payload.contains("refresh"));
        assertTrue(!payload.contains("token_hash"));
        assertTrue(!payload.contains("subscription"));
        assertTrue(!payload.contains("address"));
    }

    @Test
    void eachTokenHasUniqueId() {
        UUID userId = UUID.randomUUID();

        String first = jwtService.generateAccessToken(userId);
        String second = jwtService.generateAccessToken(userId);

        assertTrue(!first.equals(second));
        assertTrue(!jwtService.decode(first).getId()
                .equals(jwtService.decode(second).getId()));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("AA") ? "BB" : "AA");

        assertThrows(JwtException.class, () -> jwtService.decode(tampered));
    }

    @Test
    void rejectsForeignSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair foreign = generator.generateKeyPair();
        JwtService foreignService = new JwtService(
                testProperties(foreign, ISSUER, AUDIENCE, Duration.ofMinutes(15)));

        String foreignToken = foreignService.generateAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> jwtService.decode(foreignToken));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        JwtService otherIssuer = new JwtService(
                testProperties(keyPair, "other-issuer", AUDIENCE, Duration.ofMinutes(15)));

        String token = otherIssuer.generateAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> jwtService.decode(token));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        JwtService otherAudience = new JwtService(
                testProperties(keyPair, ISSUER, "other-audience", Duration.ofMinutes(15)));

        String token = otherAudience.generateAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> jwtService.decode(token));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = manualToken(Map.of(
                "iss", ISSUER,
                "sub", UUID.randomUUID().toString(),
                "aud", List.of(AUDIENCE),
                "jti", UUID.randomUUID().toString(),
                "iat", Instant.now().minus(Duration.ofMinutes(10)).getEpochSecond(),
                "exp", Instant.now().minus(Duration.ofMinutes(5)).getEpochSecond()));

        assertThrows(JwtException.class, () -> jwtService.decode(token));
    }

    @Test
    void rejectsTokenWithoutAudience() throws Exception {
        String token = manualToken(Map.of(
                "iss", ISSUER,
                "sub", UUID.randomUUID().toString(),
                "jti", UUID.randomUUID().toString(),
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond()));

        assertThrows(JwtException.class, () -> jwtService.decode(token));
    }

    @Test
    void rejectsTokenWithoutSubject() throws Exception {
        String token = manualToken(Map.of(
                "iss", ISSUER,
                "aud", List.of(AUDIENCE),
                "jti", UUID.randomUUID().toString(),
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond()));

        assertThrows(JwtException.class, () -> jwtService.decode(token));
    }

    @Test
    void rejectsNonUuidSubject() throws Exception {
        String token = manualToken(Map.of(
                "iss", ISSUER,
                "sub", "user@example.com",
                "aud", List.of(AUDIENCE),
                "jti", UUID.randomUUID().toString(),
                "iat", Instant.now().getEpochSecond(),
                "exp", Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond()));

        assertThrows(JwtException.class, () -> jwtService.parseUserId(token));
    }

    @Test
    void rejectsBlankToken() {
        assertThrows(JwtException.class, () -> jwtService.decode(null));
        assertThrows(JwtException.class, () -> jwtService.decode(""));
        assertThrows(JwtException.class, () -> jwtService.decode("   "));
        assertThrows(JwtException.class, () -> jwtService.decode("not-a-jwt"));
    }

    @Test
    void invalidTokenNeverYieldsAuthentication() {
        String tampered = jwtService.generateAccessToken(UUID.randomUUID()) + "tampered";

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        try {
            Jwt jwt = jwtService.decode(tampered);
            converter.convert(jwt);
            throw new AssertionError("Expected JwtException");
        } catch (JwtException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    void nullDependenciesRejected() {
        assertThrows(NullPointerException.class, () -> new JwtService(null));
        assertThrows(NullPointerException.class, () -> jwtService.generateAccessToken(null));
        assertThrows(NullPointerException.class,
                () -> new JwtAuthenticationConverter().convert(null));
    }

    private String manualToken(Map<String, Object> claims) throws Exception {
        JWSObject signed = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(), new Payload(toJson(claims)));
        signed.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return signed.serialize();
    }

    private static String toJson(Map<String, Object> claims) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String text) {
                json.append('"').append(text).append('"');
            } else if (value instanceof Number number) {
                json.append(number);
            } else if (value instanceof List<?> list) {
                json.append('[');
                boolean innerFirst = true;
                for (Object item : list) {
                    if (!innerFirst) {
                        json.append(',');
                    }
                    innerFirst = false;
                    json.append('"').append(item).append('"');
                }
                json.append(']');
            } else {
                throw new IllegalArgumentException("Unsupported claim value: " + value);
            }
        }
        return json.append('}').toString();
    }

    static JwtProperties testProperties(KeyPair keys, String issuer, String audience,
                                        Duration lifetime) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(issuer);
        properties.setAudience(audience);
        properties.setAccessTokenLifetime(lifetime);
        properties.setPrivateKey(toPem("PRIVATE KEY", keys.getPrivate().getEncoded()));
        properties.setPublicKey(toPem("PUBLIC KEY", ((RSAPublicKey) keys.getPublic()).getEncoded()));
        return properties;
    }

    private static String toPem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }
}
