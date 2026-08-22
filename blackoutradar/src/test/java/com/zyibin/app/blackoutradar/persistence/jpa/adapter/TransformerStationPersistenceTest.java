package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.TransformerStationJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TransformerStationPersistenceTest {

    @Autowired
    private TransformerStationPort stationPort;

    @Autowired
    private TransformerStationJpaRepository stationRepository;

    @Test
    void saveAndFindByNameRoundTrip() {
        UUID id = UUID.randomUUID();
        TransformerStation station = TransformerStation.of(id, "ТП-101");

        TransformerStation saved = stationPort.save(station);

        assertEquals(id, saved.id());

        Optional<TransformerStation> found = stationPort.findByName("ТП-101");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals("ТП-101", found.get().name());
    }

    @Test
    void findAbsentNameReturnsEmpty() {
        assertTrue(stationPort.findByName("ТП-999").isEmpty());
    }

    @Test
    void duplicateNameIsRejected() {
        stationPort.save(TransformerStation.of(UUID.randomUUID(), "ТП-101"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            stationPort.save(TransformerStation.of(UUID.randomUUID(), "ТП-101"));
            stationRepository.flush();
        });
    }
}