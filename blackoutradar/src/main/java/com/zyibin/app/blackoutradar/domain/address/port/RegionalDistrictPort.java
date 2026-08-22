package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import java.util.Optional;

public interface RegionalDistrictPort {

    Optional<RegionalDistrict> findByRegionAndTypeAndName(Region region, RegionalDistrictType type, String name);

    RegionalDistrict save(RegionalDistrict regionalDistrict);
}