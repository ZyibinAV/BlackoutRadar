package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.TransformerStationEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = MapperTestConfiguration.class)
class TransformerStationMapperTest {

    @Autowired
    private TransformerStationMapper mapper;

    @Test
    void mapsDomainToEntity() {
        UUID id = UUID.randomUUID();
        TransformerStation station = TransformerStation.of(id, "ТП-101");

        TransformerStationEntity entity = mapper.toEntity(station);

        assertEquals(id, entity.getId());
        assertEquals("ТП-101", entity.getName());
    }

    @Test
    void mapsEntityToDomain() {
        TransformerStationEntity entity = new TransformerStationEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("ТП-101");

        TransformerStation station = mapper.toDomain(entity);

        assertEquals(entity.getId(), station.id());
        assertEquals("ТП-101", station.name());
    }

    @Test
    void roundTripPreservesIdentity() {
        TransformerStation original = TransformerStation.of(UUID.randomUUID(), "ТП-101");

        TransformerStation restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.name(), restored.name());
    }
}