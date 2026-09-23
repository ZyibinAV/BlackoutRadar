package com.zyibin.app.blackoutradar.infrastructure.security.oauth2;

/**
 * OAuth2 providers supported through the common external identity flow.
 *
 * <p>Provider-specific details never leave the Security/Infrastructure
 * boundary: by the time authentication proceeds, every provider is
 * represented only by {@link ExternalIdentityData}.
 */
public enum OAuth2Provider {
    GITHUB,
    VK
}
