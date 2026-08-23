package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import java.util.Optional;

public interface CityPort {

    Optional<City> findByRegionAndName(Region region, String name);

    Optional<City> findByRegionAndRegionalDistrictAndName(Region region, RegionalDistrict regionalDistrict, String name);

    City save(City city);

    City resolveCanonicalInRegion(Region region, String canonicalName);

    City resolveCanonicalInRegionalDistrict(RegionalDistrict regionalDistrict, String canonicalName);
}