package com.zyibin.app.blackoutradar.application.address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.address.Address;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AddressCanonicalResolutionConcurrencyTest {

    @Autowired
    private AddressService addressService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void concurrentCanonicalResolutionCreatesSingleRow() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        // City directly under Region (no RegionalDistrict) to match ADR-010 City without RegionalDistrict check
        AddressInput input = new AddressInput(
                "Регион-" + uniqueSuffix,
                null,
                null,
                "Город-" + uniqueSuffix,
                "РайонГорода-" + uniqueSuffix,
                "ул Ленина-" + uniqueSuffix,
                "99");

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<Future<AddressResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    Address address;
                    try {
                        address = template.execute(status -> addressService.resolve(input));
                        if (address == null) {
                            throw new IllegalStateException("address is null");
                        }
                        return new AddressResult(address.id(), address.street().id(),
                                address.street().city().id(), address.street().city().region().id(),
                                address.cityDistrict().id());
                    } finally {
                        done.countDown();
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers not ready");
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers not finished");

            Set<UUID> addressIds = new HashSet<>();
            Set<UUID> streetIds = new HashSet<>();
            Set<UUID> cityIds = new HashSet<>();
            Set<UUID> regionIds = new HashSet<>();
            Set<UUID> cityDistrictIds = new HashSet<>();

            for (Future<AddressResult> f : futures) {
                AddressResult r = f.get(5, TimeUnit.SECONDS);
                addressIds.add(r.addressId());
                streetIds.add(r.streetId());
                cityIds.add(r.cityId());
                regionIds.add(r.regionId());
                cityDistrictIds.add(r.cityDistrictId());
            }

            assertEquals(1, addressIds.size(), "All workers must get same Address id");
            assertEquals(1, streetIds.size(), "All workers must get same Street id");
            assertEquals(1, cityIds.size(), "All workers must get same City id");
            assertEquals(1, regionIds.size(), "All workers must get same Region id");
            assertEquals(1, cityDistrictIds.size(), "All workers must get same CityDistrict id");

            // Physical DB verification scoped by canonical identity
            TransactionTemplate verifyTemplate = new TransactionTemplate(transactionManager);
            verifyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verifyTemplate.execute(status -> {
                // Resolve once more to get canonical entity values for predicates
                Address verify = addressService.resolve(input);
                // Use verify's canonical values for scoped counts
                String canonicalRegionName = verify.street().city().region().name();
                UUID regionId = verify.street().city().region().id();
                String canonicalCityName = verify.street().city().name();
                UUID cityId = verify.street().city().id();
                String canonicalCityDistrictName = verify.cityDistrict().name();
                // cityDistrict verification uses city_id
                String canonicalStreetName = verify.street().canonicalName();
                String streetType = verify.street().type().name();
                UUID streetId = verify.street().id();
                UUID cityDistrictId = verify.cityDistrict().id();
                String canonicalHouse = verify.house().canonicalHouse();

                // EntityManager scoping via fresh transaction's EntityManager
                EntityManager em = entityManager;

                long regionCount = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM region WHERE name = :name")
                        .setParameter("name", canonicalRegionName).getSingleResult()).longValue();
                assertEquals(1, regionCount, "Region physical row must be 1 for canonical name=" + canonicalRegionName);

                long cityCount = ((Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM city WHERE region_id = :regionId AND name = :name AND regional_district_id IS NULL")
                        .setParameter("regionId", regionId)
                        .setParameter("name", canonicalCityName)
                        .getSingleResult()).longValue();
                assertEquals(1, cityCount, "City physical row must be 1 for region_id=" + regionId + " name=" + canonicalCityName);

                long cityDistrictCount = ((Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM city_district WHERE city_id = :cityId AND name = :name")
                        .setParameter("cityId", cityId)
                        .setParameter("name", canonicalCityDistrictName)
                        .getSingleResult()).longValue();
                assertEquals(1, cityDistrictCount, "CityDistrict physical row must be 1 for city_id=" + cityId + " name=" + canonicalCityDistrictName);

                long streetCount = ((Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM street WHERE city_id = :cityId AND type = CAST(:type AS VARCHAR) AND canonical_name = :name")
                        .setParameter("cityId", cityId)
                        .setParameter("type", streetType)
                        .setParameter("name", canonicalStreetName)
                        .getSingleResult()).longValue();
                assertEquals(1, streetCount, "Street physical row must be 1 for city_id=" + cityId + " type=" + streetType + " name=" + canonicalStreetName);

                long addressCount = ((Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM address WHERE street_id = :streetId AND city_district_id = :cityDistrictId AND canonical_house = :house")
                        .setParameter("streetId", streetId)
                        .setParameter("cityDistrictId", cityDistrictId)
                        .setParameter("house", canonicalHouse)
                        .getSingleResult()).longValue();
                assertEquals(1, addressCount, "Address physical row must be 1 for street_id=" + streetId + " city_district_id=" + cityDistrictId + " house=" + canonicalHouse);

                return null;
            });

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private record AddressResult(UUID addressId, UUID streetId, UUID cityId, UUID regionId, UUID cityDistrictId) {}
}
