package com.zyibin.app.blackoutradar.persistence.jpa.mapper;

import com.zyibin.app.blackoutradar.domain.address.Address;
import com.zyibin.app.blackoutradar.domain.address.CityDistrict;
import com.zyibin.app.blackoutradar.domain.address.House;
import com.zyibin.app.blackoutradar.domain.address.Street;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.AddressEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.CityDistrictEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.StreetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {StreetMapper.class, CityDistrictMapper.class, CityMapper.class})
public interface AddressMapper {

    @Mapping(target = "city", source = "street.city")
    @Mapping(target = "houseNumber", source = "house.houseNumber")
    @Mapping(target = "houseAddition", source = "house.houseAddition")
    @Mapping(target = "canonicalHouse", source = "house.canonicalHouse")
    AddressEntity toEntity(Address address);

    Address toDomain(AddressEntity entity);

    Street toStreet(StreetEntity entity);

    CityDistrict toCityDistrict(CityDistrictEntity entity);

    @ObjectFactory
    default Address createAddress(AddressEntity entity) {
        House house = new House(entity.getHouseNumber(), entity.getHouseAddition(), entity.getCanonicalHouse());
        if (entity.getCityDistrict() == null) {
            return Address.of(entity.getId(), toStreet(entity.getStreet()), house);
        }
        return Address.of(entity.getId(), toStreet(entity.getStreet()), toCityDistrict(entity.getCityDistrict()), house);
    }
}