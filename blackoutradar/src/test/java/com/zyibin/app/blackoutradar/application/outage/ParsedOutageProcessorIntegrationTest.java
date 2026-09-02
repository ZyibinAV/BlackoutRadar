package com.zyibin.app.blackoutradar.application.outage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ParsedOutageProcessorIntegrationTest {

    @Autowired ParsedOutageProcessor processor;

    @Test
    void resolvesToCanonicalAddressesViaRealService() {
        Source source = Source.of(UUID.randomUUID(), "src", "ТЕЛЕГРАМ", "Официальный", "0 6 * * *", true);
        AddressInput in1 = new AddressInput("Омская область", null, null, "Омск", null, "ул Ленина", "15");
        AddressInput in2 = new AddressInput("Омская область", null, null, "Омск", null, "ул Мира", "10");
        ParsedOutage po = new ParsedOutage(source.id(), Instant.now(), Instant.now().plusSeconds(3600), "r", null, List.of(in1, in2));

        List<Address> result = processor.resolveAddresses(po);

        assertEquals(2, result.size());
        assertEquals("ЛЕНИНА", result.get(0).street().canonicalName());
        assertEquals("МИРА", result.get(1).street().canonicalName());
        List<Address> again = processor.resolveAddresses(po);
        assertEquals(result.get(0).id(), again.get(0).id());
    }
}
