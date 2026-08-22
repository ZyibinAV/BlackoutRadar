package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourcePersistenceTest {

    @Autowired
    private SourcePort sourcePort;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void saveAndFindByIdWithConfigurationRoundTrip() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "{\"channel\": \"telegram\"}", "0 6 * * *", true);

        Source saved = sourcePort.save(source);

        Optional<Source> found = sourcePort.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
        assertEquals(saved.name(), found.get().name());
        assertEquals(saved.sourceType(), found.get().sourceType());
        assertEquals(saved.providerType(), found.get().providerType());
        assertEquals("{\"channel\": \"telegram\"}", found.get().configuration());
        assertEquals(saved.schedule(), found.get().schedule());
        assertEquals(saved.isActive(), found.get().isActive());
    }

    @Test
    void saveAndFindByIdWithoutConfigurationRoundTripNullToSqlNull() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        Source saved = sourcePort.save(source);

        Optional<Source> found = sourcePort.findById(saved.id());

        assertTrue(found.isPresent());
        assertNull(found.get().configuration());
    }

    @Test
    void inactiveSourcePreserved() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", false);

        Source saved = sourcePort.save(source);

        Optional<Source> found = sourcePort.findById(saved.id());

        assertTrue(found.isPresent());
        assertTrue(!found.get().isActive());
    }

    @Test
    void updateReplacesConfiguration() {
        UUID id = UUID.randomUUID();
        Source withConfiguration = Source.of(id, "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "{\"channel\": \"telegram\"}", "0 6 * * *", true);
        Source withoutConfiguration = Source.of(id, "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        sourcePort.save(withConfiguration);
        entityManager.flush();
        entityManager.clear();

        sourcePort.save(withoutConfiguration);
        entityManager.flush();
        entityManager.clear();

        Optional<Source> found = sourcePort.findById(id);

        assertTrue(found.isPresent());
        assertNull(found.get().configuration());
        // Verify full round-trip Domain -> Persistence -> PostgreSQL -> Persistence -> Domain
        assertEquals(id, found.get().id());
        assertEquals("Горэлектросеть", found.get().name());
    }

    @Test
    void findAbsentReturnsEmpty() {
        assertTrue(sourcePort.findById(UUID.randomUUID()).isEmpty());
    }
}
