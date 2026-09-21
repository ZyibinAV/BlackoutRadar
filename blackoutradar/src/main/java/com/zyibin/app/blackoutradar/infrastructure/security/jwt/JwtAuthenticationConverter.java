package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import java.util.List;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps a validated JWT to Spring Security authentication without touching the
 * database or the business domain model.
 *
 * <p>The converter runs only after {@link JwtService} has verified signature,
 * issuer, audience, expiration and required claims, so reaching this point
 * implies a valid access token. Role mapping belongs to TASK 31; until then
 * the token carries no authorities.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt must not be null");
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }
}
