package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh token lifecycle: issuance, rotation, replay detection and
 * family-wide revocation.
 *
 * <p>Security/application boundary (ADR-009, ADR-016): no domain entity, no
 * domain port, no security state in the business domain model. Raw tokens are
 * never persisted and never logged; the database holds only SHA-256 hashes.
 *
 * <p>Rotation is atomic: the database serializes concurrent attempts through
 * a single conditional update, so at most one request wins. Reusing an
 * already revoked token is treated as replay and revokes the whole rotation
 * family without touching other families of the same user. All failures use
 * one generic authentication message and never reveal token existence, hashes,
 * family ids or internal reasons.
 */
@Service
public class RefreshTokenService {

    private static final String GENERIC_MESSAGE = "Bad credentials";
    private static final int RAW_TOKEN_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenJpaRepository repository;
    private final RefreshTokenProperties properties;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenJpaRepository repository,
                               RefreshTokenProperties properties,
                               JwtService jwtService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService must not be null");
        if (properties.getLifetime() == null
                || properties.getLifetime().isZero()
                || properties.getLifetime().isNegative()) {
            throw new IllegalArgumentException("Refresh token lifetime must be positive");
        }
    }

    /**
     * Starts a new authentication session: a fresh rotation family with its
     * first refresh token plus a new access token.
     */
    @Transactional
    public TokenPair createSession(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        UUID familyId = UUID.randomUUID();
        String rawToken = generateRawToken();
        persist(userId, familyId, rawToken);
        return new TokenPair(jwtService.generateAccessToken(userId), rawToken);
    }

    /**
     * Rotates one active refresh token: revokes it atomically, creates its
     * successor in the same family and issues a new access token.
     *
     * <p>The transaction commits even on authentication failure so that
     * replay-triggered family revocation is never rolled back together with
     * the generic error reported to the caller.
     *
     * @throws BadCredentialsException with a generic message for expired,
     *     unknown, revoked or replayed tokens. A token that is both revoked
     *     and expired is treated as replay: revocation takes priority over
     *     expiration, so its family is still revoked.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenPair refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException(GENERIC_MESSAGE);
        }
        String hash = hash(rawRefreshToken);
        Instant now = Instant.now();

        int revoked = repository.revokeIfActive(hash, now);
        if (revoked == 1) {
            RefreshTokenEntity previous = repository.findByTokenHash(hash)
                    .orElseThrow(() -> new BadCredentialsException(GENERIC_MESSAGE));
            String successor = generateRawToken();
            persist(previous.getUserId(), previous.getFamilyId(), successor);
            return new TokenPair(
                    jwtService.generateAccessToken(previous.getUserId()), successor);
        }

        handleFailedRotation(hash, now);
        throw new BadCredentialsException(GENERIC_MESSAGE);
    }

    /**
     * Ends the authentication session the given token belongs to by revoking
     * every still-active token of its rotation family. Unknown tokens are a
     * silent no-op so that logout never reveals token existence.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        repository.findByTokenHash(hash(rawRefreshToken)).ifPresent(record -> {
            repository.revokeFamily(record.getFamilyId(), now);
            log.warn("Authentication session ended");
        });
    }

    private void handleFailedRotation(String hash, Instant now) {
        Optional<RefreshTokenEntity> record = repository.findByTokenHash(hash);
        if (record.isEmpty()) {
            return;
        }
        RefreshTokenEntity entity = record.get();
        // ADR-016 priority: a revoked token is always treated as reuse,
        // even when it is also expired. Expiration alone only rejects
        // rotation of a never-revoked token without touching its family.
        if (entity.getRevokedAt() != null) {
            repository.revokeFamily(entity.getFamilyId(), now);
            log.warn("Refresh token reuse detected; rotation family revoked");
            return;
        }
        if (!entity.getExpiresAt().isAfter(now)) {
            return;
        }
    }

    private void persist(UUID userId, UUID familyId, String rawToken) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setFamilyId(familyId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plus(properties.getLifetime()));
        repository.save(entity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes the stored SHA-256 hex representation of a raw token.
     * The function itself is not a secret; secrecy lives in the raw token.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
