package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtService;
import com.zyibin.app.blackoutradar.infrastructure.security.jwt.JwtTestConfiguration;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.RefreshTokenService;
import com.zyibin.app.blackoutradar.infrastructure.security.refresh.TokenPair;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.ExternalIdentityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Real Spring Security OAuth2 login flow through the production component
 * chain with only provider HTTP replaced by a mock server:
 *
 * <pre>
 * Spring Security user service (GitHub enrichment / VK default)
 *     ↓
 * OAuth2AuthenticationToken (successful OAuth2 authentication)
 *     ↓
 * OAuth2AuthenticationSuccessHandler
 *     ↓
 * OAuth2LoginService
 *     ↓
 * provider mapper
 *     ↓
 * ExternalIdentityData
 *     ↓
 * AuthService.login
 *     ↓
 * User / ExternalIdentity
 *     ↓
 * JWT + Refresh Token
 * </pre>
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
class OAuth2LoginFlowTest {

    private static final String GITHUB_USER_URI = "https://mock.local/github-user";
    private static final String VK_USER_URI = "https://mock.local/vk-user";

    @Autowired
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ExternalIdentityRepository identityRepository;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void githubLoginFlowIssuesOrdinaryTokenSession() {
        server.expect(requestTo(GITHUB_USER_URI)).andRespond(withSuccess(
                "{\"id\":83964673,\"login\":\"octocat\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GitHubOAuth2UserService.EMAILS_URI)).andRespond(withSuccess(
                "[{\"email\":\"octo-" + UUID.randomUUID() + "@example.com\","
                        + "\"primary\":true,\"verified\":true}]",
                MediaType.APPLICATION_JSON));

        GitHubOAuth2UserService gitHubUserService = new GitHubOAuth2UserService();
        gitHubUserService.setRestOperations(restTemplate);
        gitHubUserService.setEmailRestOperations(restTemplate);
        OAuth2User oauthUser = gitHubUserService.loadUser(request("github", GITHUB_USER_URI, "id"));

        TokenPair pair = successHandler.onAuthenticationSuccess(
                new OAuth2AuthenticationToken(oauthUser, oauthUser.getAuthorities(), "github"));

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        UUID userId = jwtService.parseUserId(pair.accessToken());
        assertEquals(userId, identityRepository
                .findByProviderAndProviderSubject("GITHUB", "83964673").orElseThrow().getUserId());
        TokenPair rotated = refreshTokenService.refresh(pair.refreshToken());
        assertEquals(userId, jwtService.parseUserId(rotated.accessToken()));
        server.verify();
    }

    @Test
    void vkLoginFlowIssuesOrdinaryTokenSession() {
        String subject = "vk-sub-" + UUID.randomUUID();
        String email = "vk-" + UUID.randomUUID() + "@example.com";
        server.expect(requestTo(VK_USER_URI)).andRespond(withSuccess(
                "{\"sub\":\"" + subject + "\",\"email\":\"" + email + "\"}",
                MediaType.APPLICATION_JSON));

        DefaultOAuth2UserService vkUserService = new DefaultOAuth2UserService();
        vkUserService.setRestOperations(restTemplate);
        OAuth2User oauthUser = vkUserService.loadUser(request("vk", VK_USER_URI, "sub"));

        TokenPair pair = successHandler.onAuthenticationSuccess(
                new OAuth2AuthenticationToken(oauthUser, oauthUser.getAuthorities(), "vk"));

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        UUID userId = jwtService.parseUserId(pair.accessToken());
        assertEquals(userId, identityRepository
                .findByProviderAndProviderSubject("VK", subject).orElseThrow().getUserId());
        TokenPair rotated = refreshTokenService.refresh(pair.refreshToken());
        assertEquals(userId, jwtService.parseUserId(rotated.accessToken()));
        server.verify();
    }

    @Test
    void unknownProviderRegistrationIsRejected() {
        OAuth2User oauthUser =
                new DefaultOAuth2User(List.of(), Map.of("sub", "some-subject"), "sub");

        OAuth2AuthenticationException failure = assertThrows(OAuth2AuthenticationException.class,
                () -> successHandler.onAuthenticationSuccess(
                        new OAuth2AuthenticationToken(oauthUser, oauthUser.getAuthorities(), "unknown")));

        assertTrue(!failure.getMessage().contains("unknown"));
        assertTrue(!failure.getMessage().contains("some-subject"));
    }

    private OAuth2UserRequest request(String registrationId, String userInfoUri, String nameAttribute) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://mock.local/authorize")
                .tokenUri("https://mock.local/token")
                .userInfoUri(userInfoUri)
                .userNameAttributeName(nameAttribute)
                .clientName("Test")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, token);
    }
}
