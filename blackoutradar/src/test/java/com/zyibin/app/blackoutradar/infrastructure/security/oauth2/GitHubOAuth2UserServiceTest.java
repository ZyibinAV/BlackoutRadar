package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GitHubOAuth2UserServiceTest {

    private static final String USER_INFO_URI = "https://mock.local/user";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private GitHubOAuth2UserService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new GitHubOAuth2UserService();
        service.setRestOperations(restTemplate);
        service.setEmailRestOperations(restTemplate);
    }

    @Test
    void githubResponseIsEnrichedWithConfirmedEmails() {
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess(
                "{\"id\":83964673,\"login\":\"octocat\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GitHubOAuth2UserService.EMAILS_URI)).andRespond(withSuccess(
                "[{\"email\":\"other@example.com\",\"primary\":false,\"verified\":true},"
                        + "{\"email\":\"octo@example.com\",\"primary\":true,\"verified\":true}]",
                MediaType.APPLICATION_JSON));

        OAuth2User user = service.loadUser(request("github", USER_INFO_URI, "id"));

        assertEquals(83964673, user.getAttributes().get("id"));
        ExternalIdentityData data =
                new GitHubIdentityMapper().map(user.getAttributes());
        assertEquals(OAuth2Provider.GITHUB, data.provider());
        assertEquals("83964673", data.subject());
        assertEquals("octo@example.com", data.email());
        assertTrue(data.emailVerified());
        server.verify();
    }

    @Test
    void otherProvidersPassThroughUnchanged() {
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess(
                "{\"sub\":\"vk-user-42\",\"email\":\"vk@example.com\"}", MediaType.APPLICATION_JSON));

        OAuth2User user = service.loadUser(request("vk", USER_INFO_URI, "sub"));

        assertEquals("vk-user-42", user.getAttributes().get("sub"));
        assertFalse(user.getAttributes().containsKey(GitHubOAuth2UserService.EMAILS_ATTRIBUTE));
        server.verify();
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
