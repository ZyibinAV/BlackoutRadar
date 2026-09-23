package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Converts a GitHub identity response to provider-neutral data.
 *
 * <p>The stable GitHub numeric user {@code id} is the only identity key:
 * neither the login nor the email is used as one. The email is taken
 * exclusively from a confirmed (verified) primary address supplied under
 * the {@code emails} attribute; without it the identity carries no usable
 * email and first login is rejected downstream.
 */
@Component
public class GitHubIdentityMapper {

    private static final String EMAILS_ATTRIBUTE = "emails";

    public ExternalIdentityData map(Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "attributes must not be null");
        Object id = attributes.get("id");
        if (id == null || id.toString().isBlank()) {
            throw new IllegalArgumentException("GitHub attributes contain no stable user id");
        }
        EmailSelection selection = selectVerifiedPrimaryEmail(attributes.get(EMAILS_ATTRIBUTE));
        return new ExternalIdentityData(
                OAuth2Provider.GITHUB, id.toString(), selection.email(), selection.verified());
    }

    @SuppressWarnings("unchecked")
    private EmailSelection selectVerifiedPrimaryEmail(Object emailsAttribute) {
        if (!(emailsAttribute instanceof List<?> emails)) {
            return new EmailSelection(null, false);
        }
        for (Object entry : emails) {
            if (entry instanceof Map<?, ?> email
                    && Boolean.TRUE.equals(email.get("primary"))
                    && Boolean.TRUE.equals(email.get("verified"))) {
                Object address = email.get("email");
                if (address != null && !address.toString().isBlank()) {
                    return new EmailSelection(address.toString(), true);
                }
            }
        }
        return new EmailSelection(null, false);
    }

    private record EmailSelection(String email, boolean verified) {
    }
}
