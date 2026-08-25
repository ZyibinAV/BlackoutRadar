package com.zyibin.app.blackoutradar.application.subscription;

import com.zyibin.app.blackoutradar.application.address.AddressInput;
import com.zyibin.app.blackoutradar.application.address.AddressService;
import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.identity.User;
import com.zyibin.app.blackoutradar.domain.subscription.Subscription;
import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import com.zyibin.app.blackoutradar.domain.subscription.port.SubscriptionPort;
import com.zyibin.app.blackoutradar.domain.subscription.port.TransformerStationPort;
import com.zyibin.app.blackoutradar.domain.identity.port.UserPort;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionPort subscriptionPort;
    private final UserPort userPort;
    private final TransformerStationPort transformerStationPort;
    private final AddressService addressService;

    public SubscriptionService(SubscriptionPort subscriptionPort,
                              UserPort userPort,
                              TransformerStationPort transformerStationPort,
                              AddressService addressService) {
        this.subscriptionPort = subscriptionPort;
        this.userPort = userPort;
        this.transformerStationPort = transformerStationPort;
        this.addressService = addressService;
    }

    @Transactional
    public Subscription create(String userEmail,
                               AddressInput addressInput,
                               Instant monitoringStart,
                               Instant monitoringEnd,
                               Instant serviceAccessUntil,
                               Set<String> transformerStationNames) {
        User user = userPort.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userEmail));
        Address address = addressService.resolve(addressInput);
        Set<TransformerStation> stations = resolveStations(transformerStationNames);
        Subscription subscription = Subscription.of(
                UUID.randomUUID(), user, address,
                monitoringStart, monitoringEnd, true, serviceAccessUntil, stations);
        return subscriptionPort.save(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findById(UUID id) {
        return subscriptionPort.findById(id);
    }

    @Transactional(readOnly = true)
    public Subscription getById(UUID id) {
        return subscriptionPort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Subscription not found: " + id));
    }

    @Transactional
    public Subscription activate(UUID id) {
        Subscription current = getById(id);
        Subscription next = current.activate();
        return subscriptionPort.save(next);
    }

    @Transactional
    public Subscription deactivate(UUID id) {
        Subscription current = getById(id);
        Subscription next = current.deactivate();
        return subscriptionPort.save(next);
    }

    @Transactional
    public Subscription changeMonitoringInterval(UUID id, Instant newStart, Instant newEnd) {
        Subscription current = getById(id);
        Subscription next = current.withMonitoringInterval(newStart, newEnd);
        return subscriptionPort.save(next);
    }

    @Transactional
    public Subscription changeServiceAccessUntil(UUID id, Instant newServiceAccessUntil) {
        Subscription current = getById(id);
        Subscription next = current.withServiceAccessUntil(newServiceAccessUntil);
        return subscriptionPort.save(next);
    }

    @Transactional
    public Subscription addTransformerStation(UUID subscriptionId, String stationName) {
        Subscription current = getById(subscriptionId);
        TransformerStation station = transformerStationPort.findByName(stationName)
                .orElseThrow(() -> new NoSuchElementException("TransformerStation not found: " + stationName));
        Subscription next = current.addTransformerStation(station);
        return subscriptionPort.save(next);
    }

    @Transactional
    public Subscription removeTransformerStation(UUID subscriptionId, String stationName) {
        Subscription current = getById(subscriptionId);
        TransformerStation station = transformerStationPort.findByName(stationName)
                .orElseThrow(() -> new NoSuchElementException("TransformerStation not found: " + stationName));
        Subscription next = current.removeTransformerStation(station);
        return subscriptionPort.save(next);
    }

    private Set<TransformerStation> resolveStations(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        return names.stream()
                .map(name -> transformerStationPort.findByName(name)
                        .orElseThrow(() -> new NoSuchElementException("TransformerStation not found: " + name)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
