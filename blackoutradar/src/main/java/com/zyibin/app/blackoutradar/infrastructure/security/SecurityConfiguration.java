package com.zyibin.app.blackoutradar.infrastructure.security;

import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Minimal security infrastructure for local authentication.
 * No web filter chain, no JWT, no authorization rules: those belong to later stages.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(LocalAuthenticationProvider localAuthenticationProvider) {
        Objects.requireNonNull(localAuthenticationProvider, "localAuthenticationProvider must not be null");
        return new ProviderManager(List.of(localAuthenticationProvider));
    }
}
