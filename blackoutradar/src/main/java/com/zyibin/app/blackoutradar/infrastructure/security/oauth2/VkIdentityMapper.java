package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Converts a VK identity response to provider-neutral data.
 *
 * <p>The stable VK {@code sub} identifier is the only identity key.
 * Email is optional at VK: when absent, the identity carries no usable
 * email and first login is rejected downstream.
 */
@Component
public class VkIdentityMapper {

    public ExternalIdentityData map(Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "attributes must not be null");
        Object subject = attributes.get("sub");
        if (subject == null || subject.toString().isBlank()) {
            throw new IllegalArgumentException("VK attributes contain no stable user identifier");
        }
        Object email = attributes.get("email");
        Object verified = attributes.get("email_verified");
        return new ExternalIdentityData(
                OAuth2Provider.VK,
                subject.toString(),
                email == null || email.toString().isBlank() ? null : email.toString(),
                Boolean.TRUE.equals(verified));
    }
}
