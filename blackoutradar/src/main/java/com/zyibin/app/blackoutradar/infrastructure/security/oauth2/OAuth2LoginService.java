package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import com.zyibin.app.blackoutradar.infrastructure.security.AuthService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Real OAuth2 login flow shared by all providers.
 *
 * <p>Connects a successful Spring Security OAuth2 authentication to the
 * existing token session mechanism without duplicating authentication logic:
 * the provider-specific {@code OAuth2User} is loaded through the standard
 * Spring Security user service, converted to provider-neutral
 * {@link ExternalIdentityData} by the provider mapper, and passed to the
 * existing {@link AuthService}, which issues the ordinary JWT plus Refresh
 * Token pair. GitHub additionally loads confirmed emails through
 * {@link GitHubOAuth2UserService}; other providers use the standard default
 * user service.
 */
@Service
public class OAuth2LoginService {

    private final GitHubOAuth2UserService gitHubUserService;
    private final DefaultOAuth2UserService defaultUserService;
    private final GitHubIdentityMapper gitHubMapper;
    private final VkIdentityMapper vkMapper;
    private final AuthService authService;

    public OAuth2LoginService(
            GitHubOAuth2UserService gitHubUserService,
            @Qualifier("defaultOAuth2UserService") DefaultOAuth2UserService defaultUserService,
            GitHubIdentityMapper gitHubMapper,
            VkIdentityMapper vkMapper,
            AuthService authService) {
        this.gitHubUserService =
                Objects.requireNonNull(gitHubUserService, "gitHubUserService must not be null");
        this.defaultUserService =
                Objects.requireNonNull(defaultUserService, "defaultUserService must not be null");
        this.gitHubMapper = Objects.requireNonNull(gitHubMapper, "gitHubMapper must not be null");
        this.vkMapper = Objects.requireNonNull(vkMapper, "vkMapper must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    /**
     * Completes an OAuth2 login: loads the provider user, resolves the
     * application identity and opens an ordinary token session.
     */
    public TokenPair login(OAuth2Provider provider, OAuth2UserRequest userRequest) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(userRequest, "userRequest must not be null");
        OAuth2User oauthUser = switch (provider) {
            case GITHUB -> gitHubUserService.loadUser(userRequest);
            case VK -> defaultUserService.loadUser(userRequest);
        };
        return login(provider, oauthUser);
    }

    /**
     * Completes an OAuth2 login for an already loaded provider user, e.g. from
     * a successful Spring Security OAuth2 authentication.
     */
    public TokenPair login(OAuth2Provider provider, OAuth2User oauthUser) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(oauthUser, "oauthUser must not be null");
        ExternalIdentityData data = switch (provider) {
            case GITHUB -> gitHubMapper.map(oauthUser.getAttributes());
            case VK -> vkMapper.map(oauthUser.getAttributes());
        };
        return authService.login(data);
    }
}
