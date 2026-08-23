package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetType;
import java.util.Optional;

public interface StreetPort {

    Optional<Street> findByCityAndTypeAndCanonicalName(City city, StreetType type, String canonicalName);

    Street save(Street street);

    Street resolveCanonical(City city, StreetType type, String canonicalName);
}