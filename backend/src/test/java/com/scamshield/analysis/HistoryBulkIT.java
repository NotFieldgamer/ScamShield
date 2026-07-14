package com.scamshield.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalysisSummary;
import com.scamshield.analysis.dto.AnalyzeRequest;
import com.scamshield.analysis.dto.BulkAnalysisResponse;
import com.scamshield.auth.JwtService;
import com.scamshield.auth.Role;
import com.scamshield.auth.User;
import com.scamshield.auth.UserRepository;
import com.scamshield.support.TestSecrets;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * History ({@code GET /api/v1/me/analyses}) and bulk scan ({@code POST /api/v1/analysis/bulk}) are
 * owner-only. An anonymous caller is rejected; an authenticated caller sees only their own analyses;
 * a bulk upload scores every row and those rows then appear in that caller's history.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class HistoryBulkIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    // Testcontainers owns this container's lifecycle (@Testcontainers starts/stops it), so the
    // fluent-builder "unclosed Closeable" warning is a false positive.
    @SuppressWarnings("resource")
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
    void historyIsOwnerScopedAndRejectsAnonymous() {
        // Anonymous: the route is outside every permit-all matcher, so it is 401 — never a list.
        ResponseEntity<String> anon = rest.getForEntity("/api/v1/me/analyses", String.class);
        assertThat(anon.getStatusCode()).as("anonymous history").isEqualTo(HttpStatus.UNAUTHORIZED);

        String tokenA = tokenForUser("hist-a@example.com");
        String tokenB = tokenForUser("hist-b@example.com");

        UUID aVerdict = createAnalysis(tokenA,
                "Earn daily working from home, pay a small processing fee to begin. " + UUID.randomUUID());
        createAnalysis(tokenB, "Senior backend engineer, competitive salary, on-site. " + UUID.randomUUID());

        // A sees their own analysis; B's does not appear.
        AnalysisSummary[] historyA = history(tokenA);
        assertThat(historyA).as("A's history is non-empty").isNotEmpty();
        assertThat(historyA[0].id()).isEqualTo(aVerdict);
        assertThat(historyA[0].createdAt()).isNotNull();

        AnalysisSummary[] historyB = history(tokenB);
        assertThat(historyB).extracting(AnalysisSummary::id)
                .as("B's history must not contain A's analysis").doesNotContain(aVerdict);
    }

    @Test
    void bulkScoresEveryRowAndFeedsHistory() {
        String csv = "text\n"
                + "Earn $500 daily from home, pay a processing fee to start. " + UUID.randomUUID() + "\n"
                + "Software engineer, 5 years experience, hybrid in Berlin. " + UUID.randomUUID() + "\n";

        // Anonymous bulk upload is rejected.
        ResponseEntity<String> anon = rest.postForEntity(
                "/api/v1/analysis/bulk", multipart(csv, null), String.class);
        assertThat(anon.getStatusCode()).as("anonymous bulk").isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = tokenForUser("bulk-user@example.com");
        ResponseEntity<BulkAnalysisResponse> resp = rest.postForEntity(
                "/api/v1/analysis/bulk", multipart(csv, token), BulkAnalysisResponse.class);
        assertThat(resp.getStatusCode()).as("bulk upload").isEqualTo(HttpStatus.OK);
        BulkAnalysisResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.total()).isEqualTo(2);
        assertThat(body.rows()).hasSize(2);
        assertThat(body.scam() + body.uncertain() + body.legit()).isEqualTo(2);
        assertThat(body.rows()).allSatisfy(r -> {
            assertThat(r.id()).isNotNull();
            assertThat(r.label()).isIn("LIKELY_SCAM", "LIKELY_LEGITIMATE", "UNCERTAIN");
            assertThat(r.probability()).isBetween(0.0, 1.0);
        });
        assertThat(body.rows()).extracting(BulkAnalysisResponse.Row::line).containsExactly(1, 2);

        // The scored rows are owned by the uploader and now appear in their history.
        assertThat(history(token)).as("bulk rows land in history").hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void bulkWithNoUsablePostingsIsBadRequest() {
        String token = tokenForUser("bulk-empty@example.com");
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/v1/analysis/bulk", multipart("text\n\n\n", token), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).doesNotContainIgnoringCase("exception");
    }

    // --- helpers ---------------------------------------------------------------------------

    private UUID createAnalysis(String token, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<AnalysisResponse> created = rest.exchange(
                "/api/v1/analysis", HttpMethod.POST,
                new HttpEntity<>(new AnalyzeRequest(text, "POSTING"), headers), AnalysisResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).isNotNull();
        return created.getBody().id();
    }

    private AnalysisSummary[] history(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<AnalysisSummary[]> resp = rest.exchange(
                "/api/v1/me/analyses", HttpMethod.GET, new HttpEntity<>(headers), AnalysisSummary[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(String csv, String tokenOrNull) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (tokenOrNull != null) {
            headers.setBearerAuth(tokenOrNull);
        }
        ByteArrayResource file = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "postings.csv";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", file);
        return new HttpEntity<>(form, headers);
    }

    private String tokenForUser(String email) {
        User user = users.save(new User(email, "{noop-unused}", Role.USER));
        return jwtService.generateAccessToken(user);
    }
}
