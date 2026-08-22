package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import com.zyibin.app.blackoutradar.domain.outage.PowerOutageAddress;
import com.zyibin.app.blackoutradar.domain.outage.port.PowerOutagePort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.PowerOutageMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageAddressJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.PowerOutageJpaRepository;
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
    @Transactional
    public PowerOutage save(PowerOutage powerOutage) {
        PowerOutageEntity entity = mapper.toEntity(powerOutage);
        PowerOutageEntity saved = repository.save(entity);
        addressRepository.deleteByPowerOutageId(powerOutage.id());
        if (!powerOutage.addresses().isEmpty()) {
            addressRepository.saveAll(buildAddressEntities(saved, powerOutage.addresses()));
        }
        repository.flush();
        return toDomain(saved);
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