package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.domain.outage.port.SourcePort;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourcePortActiveIntegrationTest {

    @Autowired SourcePort sourcePort;

    @Test
    void findAllActiveReturnsOnlyActive() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Source active1 = Source.of(UUID.randomUUID(), "active-" + suffix + "-1", "ТЕЛЕГРАМ", "type-" + suffix, "0 * * * * *", true);
        Source active2 = Source.of(UUID.randomUUID(), "active-" + suffix + "-2", "ТЕЛЕГРАМ", "type-" + suffix, "0 * * * * *", true);
        Source inactive = Source.of(UUID.randomUUID(), "inactive-" + suffix, "ТЕЛЕГРАМ", "type-" + suffix, "0 * * * * *", false);

        sourcePort.save(active1);
        sourcePort.save(active2);
        sourcePort.save(inactive);

        List<Source> actives = sourcePort.findAllActive();
        // Filter to our suffix to avoid interference from other tests' data
        List<Source> filtered = actives.stream()
                .filter(s -> s.name().contains(suffix))
                .collect(Collectors.toList());

        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().allMatch(Source::isActive));
        assertTrue(filtered.stream().anyMatch(s -> s.id().equals(active1.id())));
        assertTrue(filtered.stream().anyMatch(s -> s.id().equals(active2.id())));
        assertTrue(filtered.stream().noneMatch(s -> s.id().equals(inactive.id())));
    }

    @Test
    void findAllActiveExcludesInactive() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Source inactive = Source.of(UUID.randomUUID(), "inactive2-" + suffix, "ТЕЛЕГРАМ", "type-" + suffix, "0 * * * * *", false);
        sourcePort.save(inactive);

        List<Source> actives = sourcePort.findAllActive();
        boolean contains = actives.stream().anyMatch(s -> s.id().equals(inactive.id()));
        assertTrue(!contains);
    }
}
