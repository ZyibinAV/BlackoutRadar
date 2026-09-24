package com.zyibin.app.blackoutradar.infrastructure.security.refresh;

/**
 * Authentication tokens handed to the client after login or refresh.
 *
 * <p>The string representation never contains token material: logging a
 * {@code TokenPair} must not leak the access or refresh token.
 *
 * @param accessToken signed short-lived JWT access token
 * @param refreshToken raw opaque refresh token (transmitted once, never stored)
 */
public record TokenPair(String accessToken, String refreshToken) {

    @Override
    public String toString() {
        return "TokenPair{***}";
    }
}
