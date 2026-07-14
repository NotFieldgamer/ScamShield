package com.scamshield.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalyzeRequest;
import com.scamshield.auth.JwtService;
import com.scamshield.auth.Role;
import com.scamshield.auth.User;
import com.scamshield.auth.UserRepository;
import com.scamshield.support.TestSecrets;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Object-level authorization (IDOR) for {@code GET /api/v1/analysis/{id}}. An analysis created by an
 * authenticated user is owned by that user; another user requesting it by id must get 404 — never
 * the record, and never the submitted text. Anonymous analyses stay public shareable permalinks
 * (exercised by {@link AnalysisPipelineIT}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class AnalysisOwnershipIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;

    @Test
    void anotherUsersAnalysisByIdReturns404() {
        String tokenA = tokenForUser("owner-a@example.com");
        String tokenB = tokenForUser("intruder-b@example.com");

        // User A creates an analysis; it is owned by A.
        String secret = "Confidential posting for the ownership test — earn daily, pay a processing "
                + "fee to start. " + UUID.randomUUID();
        UUID verdictId = createAnalysis(tokenA, secret);

        // User B requests A's analysis by id: 404, and the submitted text never leaks.
        ResponseEntity<String> asB = rest.exchange(
                "/api/v1/analysis/" + verdictId, HttpMethod.GET, bearer(tokenB), String.class);
        assertThat(asB.getStatusCode()).as("user B reading user A's analysis").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asB.getBody()).as("raw text must not leak in the 404 body").doesNotContain("processing fee");

        // An anonymous request for an owned analysis is also 404 (existence is not revealed).
        ResponseEntity<String> anon = rest.getForEntity("/api/v1/analysis/" + verdictId, String.class);
        assertThat(anon.getStatusCode()).as("anonymous reading an owned analysis").isEqualTo(HttpStatus.NOT_FOUND);

        // Sanity: the owner can still read their own analysis.
        ResponseEntity<AnalysisResponse> asA = rest.exchange(
                "/api/v1/analysis/" + verdictId, HttpMethod.GET, bearer(tokenA), AnalysisResponse.class);
        assertThat(asA.getStatusCode()).as("owner reading own analysis").isEqualTo(HttpStatus.OK);
        assertThat(asA.getBody()).isNotNull();
        assertThat(asA.getBody().id()).isEqualTo(verdictId);
    }

    private UUID createAnalysis(String token, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<AnalysisResponse> created = rest.exchange(
                "/api/v1/analysis", HttpMethod.POST,
                new HttpEntity<>(new AnalyzeRequest(text, "POSTING"), headers), AnalysisResponse.class);
        assertThat(created.getStatusCode()).as("A creates an analysis").isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).isNotNull();
        return created.getBody().id();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String tokenForUser(String email) {
        User user = users.save(new User(email, "{noop-unused}", Role.USER));
        return jwtService.generateAccessToken(user);
    }
}
