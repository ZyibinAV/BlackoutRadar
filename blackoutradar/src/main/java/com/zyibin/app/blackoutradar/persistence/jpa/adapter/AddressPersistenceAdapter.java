package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.AddressMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.AddressJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AddressPersistenceAdapter implements AddressPort {

    private final AddressJpaRepository repository;
    private final AddressMapper mapper;

    public AddressPersistenceAdapter(AddressJpaRepository repository, AddressMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findByStreetAndCanonicalHouse(Street street, String canonicalHouse) {
        return repository.findByStreetIdAndCityDistrictIsNullAndCanonicalHouse(street.id(), canonicalHouse)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findByStreetAndCityDistrictAndCanonicalHouse(Street street, CityDistrict cityDistrict, String canonicalHouse) {
        return repository
                .findByStreetIdAndCityDistrictIdAndCanonicalHouse(street.id(), cityDistrict.id(), canonicalHouse)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Address save(Address address) {
        return mapper.toDomain(repository.save(mapper.toEntity(address)));
    }
}