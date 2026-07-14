package com.scamshield.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies stateless access tokens. This is the only place tokens are created or
 * parsed, and it delegates all cryptography to jjwt — no hand-rolled signing or parsing.
 *
 * <p>Verification uses the system clock, so an expired token is rejected. Token <em>creation</em>
 * goes through an injectable {@link Clock} purely so a test can mint an already-expired token
 * without loosening any production behaviour.
 */
@Service
public class JwtService {

    static final String ROLE_CLAIM = "role";
    static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-ttl:PT15M}") Duration accessTtl) {
        this(secret, accessTtl, Clock.systemUTC());
    }

    // Visible for testing.
    JwtService(String secret, Duration accessTtl, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. The application refuses to start without a signing key.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (256 bits) for HS256; got " + bytes.length + ".");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = accessTtl;
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        return generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(EMAIL_CLAIM, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Parse and verify a token. Throws {@link io.jsonwebtoken.JwtException} (e.g.
     * {@code ExpiredJwtException}, {@code SignatureException}) if the token is invalid,
     * tampered, or expired.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
