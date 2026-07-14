package com.scamshield.auth.dto;

/**
 * The access token returned by login and refresh. The refresh token is not in the body — it
 * rides in an httpOnly, Secure, SameSite=Strict cookie so client JavaScript can never read it.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
