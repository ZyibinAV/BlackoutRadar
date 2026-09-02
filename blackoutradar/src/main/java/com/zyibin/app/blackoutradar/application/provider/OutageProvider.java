package com.zyibin.app.blackoutradar.application.provider;

import com.zyibin.app.blackoutradar.application.outage.ParsedOutage;
import java.util.List;

public interface OutageProvider {

    String providerType();

    List<ParsedOutage> fetch(ProviderContext context);
}
