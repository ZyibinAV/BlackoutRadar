package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.identity.RegistrationResult;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.identity.UserRole;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserRegistrationConcurrencyTest {

    @Autowired
    private UserPort userPort;

    @Autowired
    private UserJpaRepository userRepository;

    private final List<UUID> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        userRepository.deleteAllById(createdUserIds);
        createdUserIds.clear();
    }

    @Test
    void concurrentRegistrationCreatesSingleUser() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<RegistrationResult> first = executor.submit(() -> registerAfterStart(email, start));
            Future<RegistrationResult> second = executor.submit(() -> registerAfterStart(email, start));
            start.countDown();

            RegistrationResult firstResult = first.get(30, TimeUnit.SECONDS);
            RegistrationResult secondResult = second.get(30, TimeUnit.SECONDS);

            long created = List.of(firstResult, secondResult).stream()
                    .filter(result -> result == RegistrationResult.CREATED).count();
            long existed = List.of(firstResult, secondResult).stream()
                    .filter(result -> result == RegistrationResult.ALREADY_EXISTS).count();
            assertEquals(1, created);
            assertEquals(1, existed);

            User canonical = userPort.findByEmail(email).orElseThrow();
            createdUserIds.add(canonical.id());
            assertTrue(canonical.isActive());
            assertEquals(email, canonical.email());
        } finally {
            executor.shutdownNow();
        }
    }

    private RegistrationResult registerAfterStart(String email, CountDownLatch start) throws Exception {
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("workers did not start in time");
        }
        return userPort.register(
                User.of(UUID.randomUUID(), email, UserRole.USER, true), "encoded-hash");
    }
}
