package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionJpaRepository;
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
class RegionPersistenceTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private RegionJpaRepository regionRepository;

    @Test
    void saveAndFindByNameRoundTrip() {
        UUID id = UUID.randomUUID();
        Region saved = regionPort.save(Region.of(id, "Омская область"));

        assertEquals(id, saved.id());
        assertEquals("Омская область", saved.name());

        Optional<Region> found = regionPort.findByName("Омская область");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals("Омская область", found.get().name());
    }

    @Test
    void findByNameAbsentReturnsEmpty() {
        Optional<Region> found = regionPort.findByName("Несуществующий регион");

        assertTrue(found.isEmpty());
    }

    @Test
    void savePreservesSuppliedId() {
        UUID id = UUID.randomUUID();

        Region saved = regionPort.save(Region.of(id, "Алтайский край"));

        assertEquals(id, saved.id());
    }

    @Test
    void uniqueNameConstraintRejectsDuplicate() {
        regionPort.save(Region.of(UUID.randomUUID(), "Тюменская область"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            regionPort.save(Region.of(UUID.randomUUID(), "Тюменская область"));
            regionRepository.flush();
        });
    }
}