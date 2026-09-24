package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import com.zyibin.app.blackoutradar.infrastructure.security.AuthenticatedUser;
import com.zyibin.app.blackoutradar.infrastructure.security.SecurityUserResolver;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps a validated JWT to Spring Security authentication with the actual
 * authorities of the user.
 *
 * <p>The converter runs only after JWT validation, so reaching this point
 * implies a valid access token. The token itself carries no role (ADR-015,
 * ADR-017): the {@code sub} user id is resolved to the current
 * {@link AuthenticatedUser} through {@link SecurityUserResolver}, which
 * rejects missing and inactive users. The converter never touches the
 * persistence layer directly and never repeats JWT validation.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecurityUserResolver userResolver;

    public JwtAuthenticationConverter(SecurityUserResolver userResolver) {
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver must not be null");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        UUID userId = parseUserId(jwt);
        AuthenticatedUser user = userResolver.resolve(userId);
        return new JwtAuthenticationToken(jwt, user.getAuthorities(), jwt.getSubject());
    }

    private UUID parseUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new JwtException("Invalid token subject");
        }
    }
}
