package com.zyibin.app.blackoutradar.application.address;

import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import java.util.Objects;

public record AddressInput(
        String region,
        String regionalDistrict,
        RegionalDistrictType regionalDistrictType,
        String city,
        String cityDistrict,
        String street,
        String house
) {
    public AddressInput {
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(city, "city must not be null");
        Objects.requireNonNull(street, "street must not be null");
        Objects.requireNonNull(house, "house must not be null");
        if (regionalDistrict != null && regionalDistrictType == null) {
            throw new IllegalArgumentException(
                    "regionalDistrictType must be provided when regionalDistrict is set");
        }
        if (regionalDistrict == null && regionalDistrictType != null) {
            throw new IllegalArgumentException(
                    "regionalDistrict must be provided when regionalDistrictType is set");
        }
    }
}
