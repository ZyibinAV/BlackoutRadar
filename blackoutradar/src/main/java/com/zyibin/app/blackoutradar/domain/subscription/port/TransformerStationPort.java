package com.zyibin.app.blackoutradar.domain.subscription.port;

import com.zyibin.app.blackoutradar.domain.subscription.TransformerStation;
import java.util.Optional;

public interface TransformerStationPort {

    Optional<TransformerStation> findByName(String name);

    TransformerStation save(TransformerStation station);
}