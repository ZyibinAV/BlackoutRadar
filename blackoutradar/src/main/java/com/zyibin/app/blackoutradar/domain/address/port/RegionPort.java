package com.zyibin.app.blackoutradar.domain.address.port;

import com.zyibin.app.blackoutradar.domain.address.Region;
import java.util.Optional;

public interface RegionPort {

    Optional<Region> findByName(String name);

    Region save(Region region);

    Region resolveCanonical(String canonicalName);
}