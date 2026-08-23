package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Street;
import java.util.Optional;

public interface AddressPort {

    Optional<Address> findByStreetAndCanonicalHouse(Street street, String canonicalHouse);

    Optional<Address> findByStreetAndCityDistrictAndCanonicalHouse(Street street, CityDistrict cityDistrict, String canonicalHouse);

    Address save(Address address);

    Address resolveCanonical(Street street, House house);

    Address resolveCanonical(Street street, CityDistrict cityDistrict, House house);
}