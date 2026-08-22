package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import java.util.Optional;

public interface CityDistrictPort {

    Optional<CityDistrict> findByCityAndName(City city, String name);

    CityDistrict save(CityDistrict cityDistrict);
}