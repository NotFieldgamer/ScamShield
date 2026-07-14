package com.scamshield.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.scamshield.auth.Role;
import com.scamshield.auth.User;
import com.scamshield.auth.UserRepository;
import com.scamshield.auth.JwtService;
import com.scamshield.report.dto.ReportRequest;
import com.scamshield.report.dto.ReportSummary;
import com.scamshield.support.TestSecrets;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The feedback loop as an attack surface (brief §H). Proves the four guards end to end: a too-new
 * account is refused; an established account is accepted; two independent reporters flip only the
 * community label (never the training set); and a moderator decision is the sole training signal.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class ReportGuardIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ReportRepository reports;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aTwoDayOldAccountCannotReport() {
        UUID posting = insertPosting();
        String token = tokenForUser("fresh@example.com", Role.USER, 2); // account is 2 days old

        ResponseEntity<String> response = postReport(token, posting, "FALSE_POSITIVE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("7 days");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE posting_id = ?",
                Integer.class, posting)).isZero();
    }

    @Test
    void anEstablishedAccountCanReport() {
        UUID posting = insertPosting();
        String token = tokenForUser("established@example.com", Role.USER, 8);

        ResponseEntity<String> response = postReport(token, posting, "CONFIRMED_SCAM");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(statusesFor(posting)).containsExactly("PENDING");
    }

    @Test
    void twoIndependentReportersFlipTheCommunityLabelButNotTheTrainingSet() {
        UUID posting = insertPosting();
        String tokenA = tokenForUser("agree-a@example.com", Role.USER, 10);
        String tokenB = tokenForUser("agree-b@example.com", Role.USER, 10);

        assertThat(postReport(tokenA, posting, "CONFIRMED_SCAM").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postReport(tokenB, posting, "CONFIRMED_SCAM").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Agreement flips both reports to the community-consensus state...
        assertThat(statusesFor(posting)).containsExactly("COMMUNITY_CONFIRMED", "COMMUNITY_CONFIRMED");
        // ...but community agreement is NOT a training signal.
        assertThat(moderatorConfirmedFor(posting)).isEmpty();
    }

    @Test
    void onlyAModeratorDecisionReachesTheTrainingSet() {
        UUID posting = insertPosting();
        String reporter = tokenForUser("reporter@example.com", Role.USER, 10);
        String moderator = tokenForUser("mod@example.com", Role.MODERATOR, 30);

        assertThat(postReport(reporter, posting, "CONFIRMED_SCAM").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        long reportId = jdbc.queryForObject(
                "SELECT id FROM reports WHERE posting_id = ?", Long.class, posting);

        ResponseEntity<String> resolved = rest.exchange(
                "/api/v1/admin/reports/" + reportId + "/resolve", HttpMethod.POST,
                jsonEntity(moderator, "{\"decision\":\"CONFIRM\"}"), String.class);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(statusesFor(posting)).containsExactly("MODERATOR_CONFIRMED");
        assertThat(moderatorConfirmedFor(posting)).hasSize(1); // now, and only now, retraining sees it
    }

    @Test
    void aUserCannotReportTheSamePostingTwice() {
        UUID posting = insertPosting();
        String token = tokenForUser("dupe@example.com", Role.USER, 8);

        assertThat(postReport(token, posting, "FALSE_POSITIVE").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postReport(token, posting, "FALSE_POSITIVE").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // --- helpers ------------------------------------------------------------------------------

    private String tokenForUser(String email, Role role, int ageDays) {
        User user = users.save(new User(email, "{noop-unused}", role));
        jdbc.update("UPDATE users SET created_at = now() - make_interval(days => ?) WHERE id = ?",
                ageDays, user.getId());
        return jwtService.generateAccessToken(user);
    }

    private UUID insertPosting() {
        return jdbc.queryForObject(
                "INSERT INTO postings (raw_text, source, content_hash) VALUES (?, 'POSTING', ?) "
                        + "RETURNING id",
                UUID.class, "Work from home, earn daily, pay a processing fee.", randomHash());
    }

    private ResponseEntity<String> postReport(String token, UUID postingId, String claim) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/reports", HttpMethod.POST,
                new HttpEntity<>(new ReportRequest(postingId, claim), headers), String.class);
    }

    private HttpEntity<String> jsonEntity(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private List<String> statusesFor(UUID postingId) {
        return jdbc.queryForList(
                "SELECT status FROM reports WHERE posting_id = ? ORDER BY id", String.class, postingId);
    }

    private List<ReportSummary> moderatorConfirmedFor(UUID postingId) {
        return reports.moderatorConfirmed().stream()
                .filter(r -> r.postingId().equals(postingId))
                .toList();
    }

    private static String randomHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
