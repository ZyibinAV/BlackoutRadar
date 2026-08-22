package com.zyibin.app.blackoutradar.domain.outage.port;

import com.zyibin.app.blackoutradar.domain.outage.PowerOutage;
import java.util.Optional;
import java.util.UUID;

public interface PowerOutagePort {

    Optional<PowerOutage> findById(UUID id);

    PowerOutage save(PowerOutage powerOutage);
}