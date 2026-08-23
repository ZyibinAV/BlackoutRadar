package com.zyibin.app.blackoutradar.application.address;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.City;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.NormalizedHouse;
import com.zyibin.app.blackoutradar.domain.address.NormalizedStreet;
import com.zyibin.app.blackoutradar.domain.address.HouseNormalizer;
import com.zyibin.app.blackoutradar.domain.address.Region;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrict;
import com.zyibin.app.blackoutradar.domain.address.RegionalDistrictType;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.domain.address.StreetNormalizer;
import com.zyibin.app.blackoutradar.domain.address.port.AddressPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.CityPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionPort;
import com.zyibin.app.blackoutradar.domain.address.port.RegionalDistrictPort;
import com.zyibin.app.blackoutradar.domain.address.port.StreetPort;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {

    private final RegionPort regionPort;
    private final RegionalDistrictPort regionalDistrictPort;
    private final CityPort cityPort;
    private final CityDistrictPort cityDistrictPort;
    private final StreetPort streetPort;
    private final AddressPort addressPort;
    private final StreetNormalizer streetNormalizer;
    private final HouseNormalizer houseNormalizer;

    public AddressService(RegionPort regionPort,
                          RegionalDistrictPort regionalDistrictPort,
                          CityPort cityPort,
                          CityDistrictPort cityDistrictPort,
                          StreetPort streetPort,
                          AddressPort addressPort) {
        this.regionPort = regionPort;
        this.regionalDistrictPort = regionalDistrictPort;
        this.cityPort = cityPort;
        this.cityDistrictPort = cityDistrictPort;
        this.streetPort = streetPort;
        this.addressPort = addressPort;
        this.streetNormalizer = new StreetNormalizer();
        this.houseNormalizer = new HouseNormalizer();
    }

    @Transactional
    public Address resolve(AddressInput input) {
        NormalizedStreet normalizedStreet = streetNormalizer.normalize(input.street());
        NormalizedHouse normalizedHouse = houseNormalizer.normalize(input.house());

        String canonicalRegion = canonicalText(input.region());
        String canonicalCity = canonicalText(input.city());
        String canonicalRegionalDistrict = input.regionalDistrict() == null
                ? null : canonicalText(input.regionalDistrict());
        String canonicalCityDistrict = input.cityDistrict() == null
                ? null : canonicalText(input.cityDistrict());

        Region region = regionPort.resolveCanonical(canonicalRegion);

        RegionalDistrict regionalDistrict = null;
        if (canonicalRegionalDistrict != null) {
            regionalDistrict = regionalDistrictPort.resolveCanonical(region,
                    input.regionalDistrictType(), canonicalRegionalDistrict);
        }

        City city;
        if (regionalDistrict != null) {
            city = cityPort.resolveCanonicalInRegionalDistrict(regionalDistrict, canonicalCity);
        } else {
            city = cityPort.resolveCanonicalInRegion(region, canonicalCity);
        }

        CityDistrict cityDistrict = null;
        if (canonicalCityDistrict != null) {
            cityDistrict = cityDistrictPort.resolveCanonical(city, canonicalCityDistrict);
        }

        Street street = streetPort.resolveCanonical(city, normalizedStreet.type(), normalizedStreet.canonicalName());

        House house = new House(normalizedHouse.houseNumber(),
                normalizedHouse.houseAddition(), normalizedHouse.canonicalHouse());
        if (cityDistrict != null) {
            return addressPort.resolveCanonical(street, cityDistrict, house);
        }
        return addressPort.resolveCanonical(street, house);
    }

    static String canonicalText(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        String trimmed = nfc.trim().replaceAll("\\s+", " ");
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
