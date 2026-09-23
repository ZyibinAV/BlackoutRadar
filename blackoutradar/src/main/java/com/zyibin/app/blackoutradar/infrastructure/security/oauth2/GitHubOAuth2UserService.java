package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * GitHub user service enriching the standard user-info response with the
 * confirmed email list.
 *
 * <p>GitHub does not reliably return an email in the user profile itself,
 * so the {@code /user/emails} endpoint is loaded with the same access token
 * and attached under the {@code emails} attribute for
 * {@link GitHubIdentityMapper}. Other providers pass through unchanged:
 * their email, when available, already arrives with the user info.
 */
@Component
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {

    static final String GITHUB_REGISTRATION_ID = "github";
    static final String EMAILS_ATTRIBUTE = "emails";
    static final String EMAILS_URI = "https://api.github.com/user/emails";

    private RestOperations emailRestOperations = new RestTemplate();

    public void setEmailRestOperations(RestOperations emailRestOperations) {
        this.emailRestOperations =
                Objects.requireNonNull(emailRestOperations, "emailRestOperations must not be null");
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        if (!GITHUB_REGISTRATION_ID.equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }
        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
        attributes.put(EMAILS_ATTRIBUTE, fetchEmails(userRequest));
        String nameAttribute = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttribute);
    }

    List<Map<String, Object>> fetchEmails(OAuth2UserRequest userRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userRequest.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<List<Map<String, Object>>> response = emailRestOperations.exchange(
                EMAILS_URI,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        return response.getBody() == null ? List.of() : List.copyOf(response.getBody());
    }
}
