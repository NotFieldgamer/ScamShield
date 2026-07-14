package com.scamshield.auth;

import com.scamshield.auth.AuthService.IssuedTokens;
import com.scamshield.auth.dto.AuthResponse;
import com.scamshield.auth.dto.LoginRequest;
import com.scamshield.auth.dto.MeResponse;
import com.scamshield.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and the token lifecycle. The refresh token is delivered only as an httpOnly,
 * Secure, SameSite=Strict cookie scoped to this path — never in a response body and never
 * readable by client JavaScript. The access token is returned in the body as a bearer token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    // Scope the cookie to the auth endpoints; no other route needs it.
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest http, HttpServletResponse response) {
        IssuedTokens tokens = auth.login(request.email(), request.password(), clientIp(http));
        setRefreshCookie(response, tokens.refreshToken(), tokens.refreshTtlSeconds());
        return AuthResponse.bearer(tokens.accessToken(), tokens.accessTtlSeconds());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http, HttpServletResponse response) {
        IssuedTokens tokens = auth.refresh(refreshToken, clientIp(http));
        setRefreshCookie(response, tokens.refreshToken(), tokens.refreshTtlSeconds());
        return AuthResponse.bearer(tokens.accessToken(), tokens.accessTtlSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http, HttpServletResponse response) {
        auth.logout(refreshToken, clientIp(http));
        setRefreshCookie(response, "", 0); // clear the cookie
    }

    private static void setRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
