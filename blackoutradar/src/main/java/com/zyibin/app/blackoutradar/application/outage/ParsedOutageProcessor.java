package com.zyibin.app.blackoutradar.application.outage;

import com.zyibin.app.blackoutradar.application.address.AddressService;
import com.zyibin.app.blackoutradar.domain.address.Address;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParsedOutageProcessor {

    private final AddressService addressService;

    public ParsedOutageProcessor(AddressService addressService) {
        this.addressService = Objects.requireNonNull(addressService, "addressService must not be null");
    }

    @Transactional
    public List<Address> resolveAddresses(ParsedOutage parsedOutage) {
        Objects.requireNonNull(parsedOutage, "parsedOutage must not be null");
        return parsedOutage.addresses().stream()
                .map(addressService::resolve)
                .toList();
    }
}
