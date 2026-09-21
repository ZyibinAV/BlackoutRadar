package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and validates signed JWT access tokens.
 *
 * <p>Security representation only: the token carries {@code iss}, {@code sub}
 * ({@code User.id}), {@code aud}, {@code iat}, {@code exp} and {@code jti}.
 * It never contains passwords, password hashes, credentials, refresh tokens,
 * token hashes or business domain objects. The domain layer never participates
 * in JWT parsing or validation.
 *
 * <p>RSA keys are parsed lazily so that missing key configuration fails fast
 * on first token operation instead of breaking application context startup.
 * Raw tokens and keys are never written to logs.
 */
@Service
public class JwtService {

    private final JwtProperties properties;

    private volatile JwtEncoder encoder;
    private volatile NimbusJwtDecoder decoder;

    public JwtService(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Issues a short-lived signed access token for the given user id.
     */
    public String generateAccessToken(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .audience(List.of(properties.getAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.getAccessTokenLifetime()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder().encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Validates signature, issuer, audience, expiration and required claims.
     *
     * @throws JwtException when the token is invalid for any reason
     */
    public Jwt decode(String token) throws JwtException {
        if (token == null || token.isBlank()) {
            throw new JwtException("Invalid token");
        }
        Jwt jwt = decoder().decode(token);
        requireClaim(jwt, JwtClaimNames.SUB);
        requireClaim(jwt, JwtClaimNames.JTI);
        return jwt;
    }

    /**
     * Validates the token and extracts the {@code sub} user id.
     *
     * @throws JwtException when the token is invalid for any reason
     */
    public UUID parseUserId(String token) throws JwtException {
        Jwt jwt = decode(token);
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new JwtException("Invalid token subject", exception);
        }
    }

    private void requireClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new JwtException("Token is missing required claim");
        }
    }

    private JwtEncoder encoder() {
        JwtEncoder result = encoder;
        if (result == null) {
            result = buildEncoder();
            encoder = result;
        }
        return result;
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder result = decoder;
        if (result == null) {
            result = buildDecoder();
            decoder = result;
        }
        return result;
    }

    private NimbusJwtEncoder buildEncoder() {
        RSAPublicKey publicKey = parsePublicKey();
        RSAPrivateKey privateKey = parsePrivateKey();
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private NimbusJwtDecoder buildDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(parsePublicKey()).build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.getAudience()));
        jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return jwtDecoder;
    }

    private RSAPrivateKey parsePrivateKey() {
        String pem = properties.getPrivateKey();
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT private key is not configured");
        }
        try {
            byte[] encoded = decodePem(pem);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT private key cannot be parsed", exception);
        }
    }

    private RSAPublicKey parsePublicKey() {
        String pem = properties.getPublicKey();
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT public key is not configured");
        }
        try {
            byte[] encoded = decodePem(pem);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key cannot be parsed", exception);
        }
    }

    private byte[] decodePem(String pem) {
        String stripped = pem.replaceAll("-----(BEGIN|END)[^-]*-----", "");
        String base64 = stripped.replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
