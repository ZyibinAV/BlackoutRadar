package com.zyibin.app.blackoutradar.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderContextTest {

    @Test
    void createsWithSourceIdAndConfiguration() {
        UUID id = UUID.randomUUID();
        ProviderContext ctx = new ProviderContext(id, "{\"key\":\"value\"}");
        assertEquals(id, ctx.sourceId());
        assertEquals("{\"key\":\"value\"}", ctx.configuration());
    }

    @Test
    void allowsNullConfiguration() {
        UUID id = UUID.randomUUID();
        ProviderContext ctx = new ProviderContext(id, null);
        assertNull(ctx.configuration());
        assertEquals(id, ctx.sourceId());
    }

    @Test
    void rejectsNullSourceId() {
        assertThrows(NullPointerException.class, () -> new ProviderContext(null, "cfg"));
    }

    @Test
    void rejectsBlankConfiguration() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ProviderContext(id, "   "));
        assertThrows(IllegalArgumentException.class, () -> new ProviderContext(id, ""));
    }

    @Test
    void doesNotContainSourceObject() {
        // Verify via reflection that record has only sourceId and configuration
        var components = ProviderContext.class.getRecordComponents();
        assertNotNull(components);
        assertEquals(2, components.length);
        assertEquals("sourceId", components[0].getName());
        assertEquals(UUID.class, components[0].getType());
        assertEquals("configuration", components[1].getName());
        assertEquals(String.class, components[1].getType());
        // Ensure no field of type Source
        for (var c : components) {
            assertNotNull(c);
            if (c.getType().getSimpleName().equals("Source")) {
                throw new AssertionError("ProviderContext must not contain Source");
            }
        }
    }

    @Test
    void configurationIsStringNotJsonNode() {
        var comp = ProviderContext.class.getRecordComponents()[1];
        assertEquals(String.class, comp.getType());
    }
}
