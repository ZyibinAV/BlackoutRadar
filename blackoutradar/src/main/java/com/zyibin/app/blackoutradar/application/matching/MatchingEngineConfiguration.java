package com.zyibin.app.blackoutradar.application.matching;

import com.zyibin.app.blackoutradar.domain.matching.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchingEngineConfiguration {

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
