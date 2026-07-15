package com.verity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.verity.support.ClerkTestAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The auth boundary, now that Clerk issues the tokens (brief §K phase 4). Replaces AuthFlowIT's
 * checks that survived the move: rotation and reuse detection are Clerk's problem now, but "an
 * expired token is rejected" and "a USER cannot reach an admin route" are still ours to prove.
 *
 * <p>Tokens here are signed with a test key (see {@link ClerkTestAuth}) because Clerk holds the real
 * one. Everything the token then passes through — RS256 verification, the issuer check, the
 * subject-to-local-user mapping, the role read from the database — is production code.
 */
@Testcontainers
@Import(ClerkTestAuth.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {ClerkTestAuth.ISSUER_PROP, ClerkTestAuth.SECRET_KEY_PROP})
class ClerkAuthIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anAnonymousRequestToAnOwnerOnlyRouteIsRejected() {
        assertThat(get("/api/v1/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anExpiredTokenIsRejected() {
        String clerkId = provision("expired@example.com", Role.USER);

        ResponseEntity<String> response = get("/api/v1/me", ClerkTestAuth.expiredTokenFor(clerkId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenSignedByAnyoneElseIsRejected() {
        String clerkId = provision("forged@example.com", Role.USER);

        // Correctly shaped and unexpired, but not signed by the key the decoder trusts.
        ResponseEntity<String> response = get("/api/v1/me", ClerkTestAuth.forgedTokenFor(clerkId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aValidTokenResolvesToTheCallersOwnLocalAccount() {
        String clerkId = provision("real@example.com", Role.USER);

        ResponseEntity<String> response = get("/api/v1/me", ClerkTestAuth.tokenFor(clerkId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The id is ours, not Clerk's: it is what every posting and report is keyed by.
        assertThat(response.getBody()).contains("\"email\":\"real@example.com\"", "\"role\":\"USER\"");
    }

    @Test
    void everyAdminRouteRequiresTheAdminRole() {
        String clerkId = provision("plain@example.com", Role.USER);
        String token = ClerkTestAuth.tokenFor(clerkId);

        assertThat(get("/api/v1/admin/reports", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/audit", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAdminRoleInTheDatabaseNotTheTokenIsWhatGrantsAccess() {
        String clerkId = provision("boss@example.com", Role.ADMIN);

        ResponseEntity<String> response = get("/api/v1/admin/reports", ClerkTestAuth.tokenFor(clerkId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- helpers ----------------------------------------------------------------------------

    /** A Clerk user with an existing local row, so provisioning never has to call Clerk. */
    private String provision(String email, Role role) {
        String clerkId = "user_" + UUID.randomUUID().toString().replace("-", "");
        User user = users.save(new User(clerkId, email));
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role.name(), user.getId());
        return clerkId;
    }

    private ResponseEntity<String> get(String path, String tokenOrNull) {
        HttpHeaders headers = new HttpHeaders();
        if (tokenOrNull != null) {
            headers.setBearerAuth(tokenOrNull);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
