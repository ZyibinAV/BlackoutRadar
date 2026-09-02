package com.zyibin.app.blackoutradar.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zyibin.app.blackoutradar.application.outage.ParsedOutage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

    static class StubProvider implements OutageProvider {
        private final String type;
        StubProvider(String type) { this.type = type; }
        @Override public String providerType() { return type; }
        @Override public List<ParsedOutage> fetch(ProviderContext context) { return List.of(); }
    }

    @Test
    void findsProviderByType() {
        OutageProvider p = new StubProvider("typeA");
        ProviderRegistry registry = new ProviderRegistry(List.of(p));
        Optional<OutageProvider> found = registry.find("typeA");
        assertTrue(found.isPresent());
        assertEquals(p, found.get());
    }

    @Test
    void unknownTypeReturnsEmpty() {
        OutageProvider p = new StubProvider("typeA");
        ProviderRegistry registry = new ProviderRegistry(List.of(p));
        Optional<OutageProvider> found = registry.find("unknown");
        assertTrue(found.isEmpty());
    }

    @Test
    void duplicateProviderTypeThrowsOnConstruction() {
        OutageProvider p1 = new StubProvider("dup");
        OutageProvider p2 = new StubProvider("dup");
        assertThrows(IllegalStateException.class, () -> new ProviderRegistry(List.of(p1, p2)));
    }

    @Test
    void duplicateCheckMessageContainsType() {
        OutageProvider p1 = new StubProvider("same");
        OutageProvider p2 = new StubProvider("same");
        var ex = assertThrows(IllegalStateException.class, () -> new ProviderRegistry(List.of(p1, p2)));
        assertTrue(ex.getMessage().contains("same"));
    }

    @Test
    void emptyRegistryFindReturnsEmpty() {
        ProviderRegistry registry = new ProviderRegistry(List.of());
        assertTrue(registry.find("any").isEmpty());
    }

    @Test
    void doesNotExposeListOrRegister() {
        // Ensure public methods only contain find (and maybe constructor)
        var methods = ProviderRegistry.class.getMethods();
        for (var m : methods) {
            String name = m.getName();
            // Ignore Object methods, constructor helpers, find
            if (m.getDeclaringClass() == Object.class) continue;
            if (name.equals("find") || name.equals("wait") || name.equals("equals") || name.equals("hashCode") || name.equals("toString") || name.equals("getClass") || name.equals("notify") || name.equals("notifyAll")) continue;
            if (name.equals("list") || name.equals("register")) {
                throw new AssertionError("Registry must not expose " + name + " as public API");
            }
        }
        // Verify no public list/register
        boolean hasList = false;
        boolean hasRegister = false;
        for (var m : ProviderRegistry.class.getDeclaredMethods()) {
            if (m.getName().equals("list")) hasList = true;
            if (m.getName().equals("register")) hasRegister = true;
        }
        assertTrue(!hasList, "Registry must not have list()");
        assertTrue(!hasRegister, "Registry must not have register()");
    }
}
