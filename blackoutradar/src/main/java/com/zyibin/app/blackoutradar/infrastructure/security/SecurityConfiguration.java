package com.zyibin.app.blackoutradar.infrastructure.security;

import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtProperties;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.RefreshTokenProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class})
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            LocalAuthenticationProvider localAuthenticationProvider) {

        Objects.requireNonNull(
                localAuthenticationProvider,
                "localAuthenticationProvider must not be null");

        return new ProviderManager(List.of(localAuthenticationProvider));
    }

    /**
     * Standard Spring Security JWT validation entry point.
     *
     * <p>The bean itself never touches signing keys: {@link JwtService}
     * parses them lazily on first use, so context startup stays independent
     * of secret configuration. There is no web filter chain yet because the
     * project has no web layer; Bearer token handling will be wired when REST
     * controllers arrive. Until then this decoder is the standard validation
     * mechanism used by token services and tests.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        Objects.requireNonNull(jwtService, "jwtService must not be null");
        return jwtService::decode;
    }

    /**
     * Standard user service for OAuth2 providers without a dedicated
     * enrichment step (currently VK).
     *
     * <p>This does not duplicate Spring Boot auto-configuration: Boot provides
     * no default {@code OAuth2UserService} bean. Declaring it explicitly keeps
     * the provider wiring in one place and injectable for the shared login
     * flow. Local Authentication, JWT and Refresh Token beans are untouched.
     */
    @Bean
    public DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }
}
