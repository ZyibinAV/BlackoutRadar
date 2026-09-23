package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import java.util.Objects;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

/**
 * Bridges a successful Spring Security OAuth2 authentication to the shared
 * login flow.
 *
 * <p>Accepts the standard {@link OAuth2AuthenticationToken} produced by a
 * successful OAuth2 login, resolves the provider strictly from its Spring
 * Security registration id ({@code github}/{@code vk} as configured), and
 * delegates everything else — user resolution, identity management and token
 * issuance — to the existing {@link OAuth2LoginService}. No user lookup,
 * creation, JWT or Refresh Token logic is duplicated here.
 *
 * <p>The resulting {@link TokenPair} is returned to the caller: the project
 * has no web layer yet, so no HTTP response or redirect is produced here.
 * Wiring this handler into a filter chain belongs to the future REST phase.
 */
@Component
public class OAuth2AuthenticationSuccessHandler {

    private final OAuth2LoginService loginService;

    public OAuth2AuthenticationSuccessHandler(OAuth2LoginService loginService) {
        this.loginService = Objects.requireNonNull(loginService, "loginService must not be null");
    }

    /**
     * Completes a successful OAuth2 authentication with an ordinary token
     * session.
     *
     * @throws OAuth2AuthenticationException for an unknown provider registration
     */
    public TokenPair onAuthenticationSuccess(OAuth2AuthenticationToken authentication) {
        Objects.requireNonNull(authentication, "authentication must not be null");
        OAuth2Provider provider = resolveProvider(authentication.getAuthorizedClientRegistrationId());
        return loginService.login(provider, authentication.getPrincipal());
    }

    private OAuth2Provider resolveProvider(String registrationId) {
        if ("github".equals(registrationId)) {
            return OAuth2Provider.GITHUB;
        }
        if ("vk".equals(registrationId)) {
            return OAuth2Provider.VK;
        }
        throw new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_REQUEST,
                "Unknown OAuth2 provider registration",
                null));
    }
}
