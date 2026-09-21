package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.RefreshTokenEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RefreshTokenJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Two requests racing on the same refresh token: exactly one rotation wins,
 * no second successor is created, and the loser observes a generic
 * authentication failure (replay of the now revoked token).
 *
 * <p>Per ADR-016 the loser's replay revokes the whole rotation family, so the
 * final state is a fully revoked two-token family, while another family of
 * the same user stays usable.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class RefreshTokenConcurrencyTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenJpaRepository repository;

    @Autowired
    private UserPort userPort;

    @Test
    void concurrentRefreshAllowsSingleRotation() throws Exception {
        User user = userPort.save(User.of(UUID.randomUUID(),
                "concurrent-" + UUID.randomUUID() + "@example.com", UserRole.USER, true));
        TokenPair first = refreshTokenService.createSession(user.id());
        TokenPair otherFamily = refreshTokenService.createSession(user.id());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Outcome> left = executor.submit(() -> attempt(first.refreshToken(), start));
            Future<Outcome> right = executor.submit(() -> attempt(first.refreshToken(), start));
            start.countDown();

            Outcome leftResult = left.get(30, TimeUnit.SECONDS);
            Outcome rightResult = right.get(30, TimeUnit.SECONDS);

            List<Outcome> outcomes = new ArrayList<>(List.of(leftResult, rightResult));
            for (Outcome outcome : outcomes) {
                if (outcome instanceof Outcome.Unexpected unexpected) {
                    throw new AssertionError("Unexpected refresh failure",
                            unexpected.failure());
                }
            }

            long successes = List.of(leftResult, rightResult).stream()
                    .filter(outcome -> outcome instanceof Outcome.Success)
                    .count();
            long failures = List.of(leftResult, rightResult).stream()
                    .filter(outcome -> outcome instanceof Outcome.ReplayRejected)
                    .count();
            assertEquals(1, successes);
            assertEquals(1, failures);

            UUID family = repository.findByTokenHash(
                    refreshTokenService.hash(first.refreshToken())).orElseThrow().getFamilyId();
            List<RefreshTokenEntity> rows = repository.findByFamilyId(family);
            String originalHash = refreshTokenService.hash(first.refreshToken());
            List<RefreshTokenEntity> originals = rows.stream()
                    .filter(row -> originalHash.equals(row.getTokenHash()))
                    .toList();
            List<RefreshTokenEntity> successors = rows.stream()
                    .filter(row -> !originalHash.equals(row.getTokenHash()))
                    .toList();

            // No third token was created: exactly the original plus one successor.
            assertEquals(2, rows.size());
            assertEquals(1, originals.size());
            assertEquals(1, successors.size());

            // The original R1 is revoked by the winning rotation.
            assertTrue(originals.get(0).getRevokedAt() != null);

            // The loser's replay revoked the whole family (ADR-016),
            // so the single successor is revoked as well.
            assertTrue(successors.get(0).getRevokedAt() != null);

            // The other family of the same user is unaffected.
            TokenPair rotatedOther = refreshTokenService.refresh(otherFamily.refreshToken());
            assertNotNull(rotatedOther.accessToken());
            assertTrue(!rotatedOther.refreshToken().equals(otherFamily.refreshToken()));
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome attempt(String rawToken, CountDownLatch start) {
        try {
            if (!start.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("workers did not start in time");
            }
            TokenPair pair = refreshTokenService.refresh(rawToken);
            return new Outcome.Success(pair.refreshToken());
        } catch (BadCredentialsException rejected) {
            return new Outcome.ReplayRejected();
        } catch (Exception failed) {
            return new Outcome.Unexpected(failed);
        }
    }

    private sealed interface Outcome permits Outcome.Success, Outcome.ReplayRejected,
            Outcome.Unexpected {
        record Success(String successor) implements Outcome {
        }

        record ReplayRejected() implements Outcome {
        }

        record Unexpected(Exception failure) implements Outcome {
        }
    }
}
