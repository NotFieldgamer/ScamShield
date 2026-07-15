package com.verity.auth;

import com.verity.audit.AuditRepository;
import com.verity.auth.dto.MeResponse;
import com.verity.common.ConflictException;
import com.verity.common.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, and the refresh-token lifecycle. Cryptography is delegated: BCrypt for
 * passwords (via the injected {@link PasswordEncoder}), jjwt for access tokens (via
 * {@link JwtService}). Refresh tokens are high-entropy random strings; only their SHA-256 hash
 * is persisted, so a database leak does not expose usable tokens.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditRepository audit;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AuditRepository audit,
                       @Value("${app.jwt.access-ttl:PT15M}") Duration accessTtl,
                       @Value("${app.refresh.ttl:P7D}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.audit = audit;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    /** The access token plus the raw refresh token the controller places in the cookie. */
    public record IssuedTokens(String accessToken, long accessTtlSeconds,
                               String refreshToken, long refreshTtlSeconds) {
    }

    @Transactional
    public MeResponse register(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("That email is already registered. Try signing in instead.");
        }
        User user = users.save(new User(normalized, passwordEncoder.encode(rawPassword), Role.USER));
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.isEmailVerified());
    }

    @Transactional
    public IssuedTokens login(String email, String rawPassword, String ip) {
        String normalized = normalizeEmail(email);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalized, rawPassword));
        } catch (AuthenticationException e) {
            // One message for both "no such user" and "wrong password" — do not reveal which.
            throw new UnauthorizedException("Invalid email or password.");
        }
        User user = users.findByEmail(normalized)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));
        IssuedTokens tokens = issue(user, UUID.randomUUID());
        audit.record(user.getId(), "LOGIN", "user", String.valueOf(user.getId()), ip);
        return tokens;
    }

    /**
     * Rotate a refresh token. Presenting a token that was already rotated (revoked) is treated
     * as replay: the whole family is revoked and a 401 is returned.
     *
     * <p>{@code noRollbackFor} is essential here — on reuse we revoke the family and then throw,
     * and that revocation must survive the exception. Without it, the rollback would silently
     * undo the family revocation and the reuse-detection guarantee would not hold.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens refresh(String rawRefreshToken, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token.");
        }
        RefreshToken current = refreshTokens.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));
        Instant now = Instant.now();

        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            audit.record(current.getUserId(), "REFRESH_REUSE_DETECTED",
                    "refresh_family", current.getFamilyId().toString(), ip);
            throw new UnauthorizedException("This session is no longer valid. Please sign in again.");
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException("Your session has expired. Please sign in again.");
        }

        // Single-use rotation, made atomic against concurrent replay. The conditional revoke
        // matches the row only while it is still active; if a racing request already rotated it,
        // this returns 0 and we treat the loss exactly like reuse — burn the family and reject.
        // Without this guard, two simultaneous presentations of the same token would both pass
        // the isRevoked() check above and each mint a live successor, silently forking the session.
        if (refreshTokens.revokeIfActive(current.getId(), now) == 0) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            audit.record(current.getUserId(), "REFRESH_REUSE_DETECTED",
                    "refresh_family", current.getFamilyId().toString(), ip);
            throw new UnauthorizedException("This session is no longer valid. Please sign in again.");
        }
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
        IssuedTokens tokens = issue(user, current.getFamilyId());
        audit.record(user.getId(), "REFRESH", "user", String.valueOf(user.getId()), ip);
        return tokens;
    }

    @Transactional
    public void logout(String rawRefreshToken, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256Hex(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            audit.record(token.getUserId(), "LOGOUT",
                    "refresh_family", token.getFamilyId().toString(), ip);
        });
    }

    private IssuedTokens issue(User user, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = randomToken();
        Instant expiresAt = Instant.now().plus(refreshTtl);
        refreshTokens.save(new RefreshToken(user.getId(), sha256Hex(rawRefresh), familyId, expiresAt));
        return new IssuedTokens(accessToken, accessTtl.toSeconds(), rawRefresh, refreshTtl.toSeconds());
    }

    private String randomToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy — SHA-256 at rest is sufficient, no BCrypt needed.
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
