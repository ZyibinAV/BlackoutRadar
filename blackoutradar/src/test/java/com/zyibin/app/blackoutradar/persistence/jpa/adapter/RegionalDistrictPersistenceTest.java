package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.RegionalDistrictJpaRepository;
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
class RegionalDistrictPersistenceTest {

    @Autowired
    private RegionPort regionPort;

    @Autowired
    private RegionalDistrictPort regionalDistrictPort;

    @Autowired
    private RegionalDistrictJpaRepository regionalDistrictRepository;

    @Test
    void saveAndFindByRegionTypeAndNameRoundTrip() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        UUID id = UUID.randomUUID();

        RegionalDistrict saved = regionalDistrictPort.save(
                RegionalDistrict.of(id, region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));

        assertEquals(id, saved.id());

        Optional<RegionalDistrict> found = regionalDistrictPort.findByRegionAndTypeAndName(
                region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район");

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals(region.id(), found.get().region().id());
        assertEquals(RegionalDistrictType.MUNICIPAL_DISTRICT, found.get().type());
        assertEquals("Омский район", found.get().name());
    }

    @Test
    void sameNameInDifferentRegionsIsAllowed() {
        Region regionA = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        Region regionB = regionPort.save(Region.of(UUID.randomUUID(), "Новосибирская область"));

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        regionalDistrictPort.save(
                RegionalDistrict.of(idA, regionA, RegionalDistrictType.MUNICIPAL_DISTRICT, "Ленинский район"));
        regionalDistrictPort.save(
                RegionalDistrict.of(idB, regionB, RegionalDistrictType.MUNICIPAL_DISTRICT, "Ленинский район"));

        assertEquals(idA, regionalDistrictPort.findByRegionAndTypeAndName(
                        regionA, RegionalDistrictType.MUNICIPAL_DISTRICT, "Ленинский район")
                .orElseThrow().id());
        assertEquals(idB, regionalDistrictPort.findByRegionAndTypeAndName(
                        regionB, RegionalDistrictType.MUNICIPAL_DISTRICT, "Ленинский район")
                .orElseThrow().id());
    }

    @Test
    void findAbsentReturnsEmpty() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));

        Optional<RegionalDistrict> found = regionalDistrictPort.findByRegionAndTypeAndName(
                region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Несуществующий район");

        assertTrue(found.isEmpty());
    }

    @Test
    void savePreservesRegionReference() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));

        RegionalDistrict saved = regionalDistrictPort.save(
                RegionalDistrict.of(UUID.randomUUID(), region, RegionalDistrictType.URBAN_OKRUG, "Округ"));

        assertEquals(region.id(), saved.region().id());
    }

    @Test
    void duplicateInSameRegionWithSameTypeAndNameIsRejected() {
        Region region = regionPort.save(Region.of(UUID.randomUUID(), "Омская область"));
        regionalDistrictPort.save(RegionalDistrict.of(
                UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            regionalDistrictPort.save(RegionalDistrict.of(
                    UUID.randomUUID(), region, RegionalDistrictType.MUNICIPAL_DISTRICT, "Омский район"));
            regionalDistrictRepository.flush();
        });
    }
}