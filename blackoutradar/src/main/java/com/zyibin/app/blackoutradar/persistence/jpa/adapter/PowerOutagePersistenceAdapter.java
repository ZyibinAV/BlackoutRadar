package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.PowerOutageMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageAddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PowerOutagePersistenceAdapter implements PowerOutagePort {

    private final PowerOutageJpaRepository repository;
    private final PowerOutageAddressJpaRepository addressRepository;
    private final PowerOutageMapper mapper;

    public PowerOutagePersistenceAdapter(PowerOutageJpaRepository repository,
                                         PowerOutageAddressJpaRepository addressRepository,
                                         PowerOutageMapper mapper) {
        this.repository = repository;
        this.addressRepository = addressRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PowerOutage> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PowerOutage> findBySourceAndExternalReference(UUID sourceId, String externalReference) {
        return repository.findBySourceIdAndExternalReference(sourceId, externalReference).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PowerOutage> findBySourceAndFallbackIdentity(UUID sourceId, Instant startTime, Set<UUID> canonicalAddressIds) {
        String fingerprint = computeFallbackFingerprint(startTime, canonicalAddressIds);
        return repository.findBySourceIdAndFallbackFingerprint(sourceId, fingerprint).map(this::toDomain);
    }

    @Override
    @Transactional
    public PowerOutage save(PowerOutage powerOutage) {
        PowerOutageEntity entity = mapper.toEntity(powerOutage);
        repository.findById(powerOutage.id()).ifPresent(existing -> {
            entity.setExternalReference(existing.getExternalReference());
            entity.setFallbackFingerprint(existing.getFallbackFingerprint());
        });
        PowerOutageEntity saved = repository.save(entity);
        addressRepository.deleteByPowerOutageId(powerOutage.id());
        if (!powerOutage.addresses().isEmpty()) {
            addressRepository.saveAll(buildAddressEntities(saved, powerOutage.addresses()));
        }
        repository.flush();
        return toDomain(saved);
    }


    @Override
    @Transactional
    public PowerOutagePort.CreateResult tryCreateWithExternalReference(PowerOutage powerOutage, String externalReference) {
        int inserted = repository.insertWithExternalReference(powerOutage.id(), powerOutage.source().id(), powerOutage.startTime(), powerOutage.endTime(), powerOutage.reason(), powerOutage.status(), externalReference);
        PowerOutageEntity entity = repository.findBySourceIdAndExternalReference(powerOutage.source().id(), externalReference).orElseThrow();
        if (inserted == 1) {
            addressRepository.deleteByPowerOutageId(entity.getId());
            if (!powerOutage.addresses().isEmpty()) {
                addressRepository.saveAll(buildAddressEntities(entity, powerOutage.addresses()));
            }
            repository.flush();
            return new PowerOutagePort.CreateResult(true, toDomain(entity));
        }
        return new PowerOutagePort.CreateResult(false, toDomain(entity));
    }

    @Override
    @Transactional
    public PowerOutagePort.CreateResult tryCreateWithFallback(PowerOutage powerOutage) {
        Set<UUID> addressIds = powerOutage.addresses().stream().map(a -> a.address().id()).collect(Collectors.toSet());
        String fingerprint = computeFallbackFingerprint(powerOutage.startTime(), addressIds);
        int inserted = repository.insertWithFallbackFingerprint(powerOutage.id(), powerOutage.source().id(), powerOutage.startTime(), powerOutage.endTime(), powerOutage.reason(), powerOutage.status(), fingerprint);
        PowerOutageEntity entity = repository.findBySourceIdAndFallbackFingerprint(powerOutage.source().id(), fingerprint).orElseThrow();
        if (inserted == 1) {
            addressRepository.deleteByPowerOutageId(entity.getId());
            if (!powerOutage.addresses().isEmpty()) {
                addressRepository.saveAll(buildAddressEntities(entity, powerOutage.addresses()));
            }
            repository.flush();
            return new PowerOutagePort.CreateResult(true, toDomain(entity));
        }
        return new PowerOutagePort.CreateResult(false, toDomain(entity));
    }

    private String computeFallbackFingerprint(Instant startTime, Set<UUID> canonicalAddressIds) {
        List<String> sorted = canonicalAddressIds.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.toList());
        String joined = startTime.toString() + "|" + String.join(",", sorted);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<PowerOutageAddressEntity> buildAddressEntities(PowerOutageEntity powerOutage,
                                                                Set<PowerOutageAddress> addresses) {
        return addresses.stream()
                .map(address -> {
                    PowerOutageAddressEntity entity = mapper.toAddressEntity(address);
                    entity.setPowerOutage(powerOutage);
                    return entity;
                })
                .collect(Collectors.toList());
    }

    private PowerOutage toDomain(PowerOutageEntity entity) {
        List<PowerOutageAddress> addresses = addressRepository.findAllByPowerOutageId(entity.getId())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
        return mapper.toDomain(entity, addresses);
    }
}