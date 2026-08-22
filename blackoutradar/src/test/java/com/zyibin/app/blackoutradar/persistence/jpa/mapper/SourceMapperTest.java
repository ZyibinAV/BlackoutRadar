package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.domain.outage.Source;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class SourceMapperTest {

    @Autowired
    private SourceMapper mapper;

    @Test
    void mapsDomainToEntityWithConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "{\"channel\": \"telegram\"}", "0 6 * * *", true);

        SourceEntity entity = mapper.toEntity(source);

        assertEquals(source.id(), entity.getId());
        assertEquals(source.name(), entity.getName());
        assertEquals(source.sourceType(), entity.getSourceType());
        assertEquals(source.providerType(), entity.getProviderType());
        assertEquals(source.configuration(), entity.getConfiguration());
        assertEquals(source.schedule(), entity.getSchedule());
        assertTrue(entity.isActive());
    }

    @Test
    void mapsDomainToEntityWithoutConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        SourceEntity entity = mapper.toEntity(source);

        assertNull(entity.getConfiguration());
        assertEquals(source.schedule(), entity.getSchedule());
    }

    @Test
    void mapsEntityToDomainWithConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "{\"channel\": \"telegram\"}", "0 6 * * *", true);

        SourceEntity entity = mapper.toEntity(source);

        Source restored = mapper.toDomain(entity);

        assertEquals(source.id(), restored.id());
        assertEquals(source.name(), restored.name());
        assertEquals(source.sourceType(), restored.sourceType());
        assertEquals(source.providerType(), restored.providerType());
        assertEquals(source.configuration(), restored.configuration());
        assertEquals(source.schedule(), restored.schedule());
        assertEquals(source.isActive(), restored.isActive());
    }

    @Test
    void mapsEntityToDomainWithoutConfiguration() {
        Source source = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        SourceEntity entity = mapper.toEntity(source);

        Source restored = mapper.toDomain(entity);

        assertNull(restored.configuration());
        assertEquals(source.schedule(), restored.schedule());
    }

    @Test
    void roundTripPreservesOptionalConfiguration() {
        Source withConfiguration = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "{\"channel\": \"telegram\"}", "0 6 * * *", true);
        Source withoutConfiguration = Source.of(UUID.randomUUID(), "Горэлектросеть", "ТЕЛЕГРАМ", "Официальный",
                "0 6 * * *", true);

        Source restoredWith = mapper.toDomain(mapper.toEntity(withConfiguration));
        Source restoredWithout = mapper.toDomain(mapper.toEntity(withoutConfiguration));

        assertEquals(withConfiguration.configuration(), restoredWith.configuration());
        assertNull(restoredWithout.configuration());
    }
}