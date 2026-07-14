package com.scamshield.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.scamshield.auth.dto.LoginRequest;
import com.scamshield.auth.dto.RegisterRequest;
import com.scamshield.support.TestSecrets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the four security guarantees required of Phase 4, end to end against a real database:
 * an expired access token is rejected, a rotated refresh token cannot be reused, reuse revokes
 * the whole family, and every admin route requires the ADMIN role.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class AuthFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private JwtService jwtService;

    @Test
    void anExpiredAccessTokenIsRejected() {
        register("expired@example.com");
        User user = users.findByEmail("expired@example.com").orElseThrow();

        // A freshly minted token is accepted — proves /me is genuinely reachable when authorized.
        ResponseEntity<String> accepted = get("/api/v1/me", jwtService.generateAccessToken(user));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).contains("expired@example.com");

        // The same subject, signed with the same key, but minted an hour in the past → expired.
        JwtService pastClock = new JwtService(TestSecrets.JWT, Duration.ofMinutes(15),
                Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));
        String expiredToken = pastClock.generateAccessToken(user);

        ResponseEntity<String> rejected = get("/api/v1/me", expiredToken);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aRotatedRefreshTokenCannotBeReused() {
        String original = registerAndLogin("rotate@example.com");

        // First refresh rotates the token: succeeds and issues a different refresh token.
        ResponseEntity<String> rotated = refresh(original);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String successor = refreshCookieOf(rotated);
        assertThat(successor).isNotBlank().isNotEqualTo(original);

        // Presenting the already-rotated (now revoked) token again is rejected.
        assertThat(refresh(original).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reuseOfARotatedTokenRevokesTheWholeFamily() {
        String original = registerAndLogin("family@example.com");

        ResponseEntity<String> rotated = refresh(original);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String successor = refreshCookieOf(rotated);
        assertThat(successor).isNotBlank();

        // Replaying the old token triggers reuse detection...
        assertThat(refresh(original).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ...which revokes the entire family, so the previously-valid successor is now dead too.
        assertThat(refresh(successor).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyAdminRouteRequiresTheAdminRole() {
        String userToken = tokenForNewUser("user@example.com", Role.USER);
        String moderatorToken = tokenForNewUser("moderator@example.com", Role.MODERATOR);
        String adminToken = tokenForNewUser("admin@example.com", Role.ADMIN);

        List<String> adminRoutes = List.of("/api/v1/admin/reports", "/api/v1/admin/audit");

        // Every /api/v1/admin/** route is ADMIN-only: a USER and a MODERATOR are both forbidden.
        for (String route : adminRoutes) {
            assertThat(get(route, userToken).getStatusCode())
                    .as("USER forbidden from " + route).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get(route, moderatorToken).getStatusCode())
                    .as("MODERATOR forbidden from " + route).isEqualTo(HttpStatus.FORBIDDEN);
        }

        // The routes are role-gated, not blanket-denied: an ADMIN reaches every one of them.
        for (String route : adminRoutes) {
            assertThat(get(route, adminToken).getStatusCode())
                    .as("ADMIN reaches " + route).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @Transactional
    void concurrentRotationOfTheSameTokenIsRejectedByTheConditionalRevoke() {
        // Deterministically pins the concurrency guard that makes rotation atomic: whatever the
        // interleaving, the DB revoke matches an active token exactly once. The first attempt to
        // retire a token affects one row; a second attempt to retire the same token affects zero
        // — which the service treats as reuse. Two racing refreshes therefore cannot both mint a
        // live successor.
        User user = users.save(new User("race@example.com", "{noop-unused}", Role.USER));
        RefreshToken token = refreshTokens.save(new RefreshToken(
                user.getId(), "hash-for-race-test", UUID.randomUUID(),
                Instant.now().plus(Duration.ofDays(7))));
        Instant now = Instant.now();

        int firstWins = refreshTokens.revokeIfActive(token.getId(), now);
        int secondLoses = refreshTokens.revokeIfActive(token.getId(), now);

        assertThat(firstWins).isEqualTo(1);
        assertThat(secondLoses).isEqualTo(0);
    }

    @Test
    void aMalformedLoginBodyReturns400NotAServerError() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\": ", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- helpers ------------------------------------------------------------------------------

    private String tokenForNewUser(String email, Role role) {
        // A password hash is required by the schema but irrelevant here; we mint the token directly.
        User user = users.save(new User(email, "{noop-unused}", role));
        return jwtService.generateAccessToken(user);
    }

    private void register(String email) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/register", new RegisterRequest(email, PASSWORD), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String registerAndLogin(String email) {
        register(email);
        ResponseEntity<String> login = rest.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, PASSWORD), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = refreshCookieOf(login);
        assertThat(cookie).as("login sets a refresh cookie").isNotBlank();
        return cookie;
    }

    private ResponseEntity<String> refresh(String refreshTokenValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, AuthController.REFRESH_COOKIE + "=" + refreshTokenValue);
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** Extracts the raw refresh-token value from a response's Set-Cookie header. */
    private static String refreshCookieOf(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        String prefix = AuthController.REFRESH_COOKIE + "=";
        for (String cookie : setCookies) {
            if (cookie.startsWith(prefix)) {
                String value = cookie.substring(prefix.length());
                int semicolon = value.indexOf(';');
                return semicolon >= 0 ? value.substring(0, semicolon) : value;
            }
        }
        return null;
    }
}
