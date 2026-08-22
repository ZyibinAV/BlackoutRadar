package com.zyibin.app.blackoutradar.domain.outage.port;

import com.zyibin.app.blackoutradar.domain.outage.Source;
import java.util.Optional;
import java.util.UUID;

public interface SourcePort {

    Optional<Source> findById(UUID id);

    Source save(Source source);
}