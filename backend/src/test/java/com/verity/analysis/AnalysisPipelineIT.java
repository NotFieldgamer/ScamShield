package com.verity.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verity.analysis.dto.AnalysisResponse;
import com.verity.analysis.dto.AnalyzeRequest;
import com.verity.inference.TfidfVectorizer;
import com.verity.support.TestSecrets;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test of the analysis pipeline over 20 fixtures, against a real pgvector
 * database (seeded with the 866 confirmed scams), real Redis, and in-process ONNX/MiniLM.
 * Verifies the done-when contract and prints the p95 latency with a per-stage breakdown.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestSecrets.JWT_PROP)
class AnalysisPipelineIT {

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
    private DataSource dataSource;
    @Autowired
    private TfidfVectorizer vectorizer;

    private static boolean seeded = false;
    private List<Fixture> fixtures;

    @BeforeEach
    void setUp() throws Exception {
        if (!seeded) {
            loadKnownScamsSeed();
            seeded = true;
        }
        fixtures = loadFixtures();
    }

    @Test
    void pipelineOverTwentyFixtures() {
        List<Long> latencies = new ArrayList<>();
        Map<String, Long> stageTotals = new LinkedHashMap<>();
        AnalysisResponse canonical = null;
        AnalysisResponse obfuscated = null;
        List<AnalysisResponse> legit = new ArrayList<>();
        UUID sampleId = null;

        for (Fixture f : fixtures) {
            long t0 = System.nanoTime();
            ResponseEntity<AnalysisResponse> resp = rest.postForEntity(
                    "/api/v1/analysis", new AnalyzeRequest(f.text(), f.kind()), AnalysisResponse.class);
            latencies.add((System.nanoTime() - t0) / 1_000_000);

            assertThat(resp.getStatusCode()).as(f.name()).isEqualTo(HttpStatus.OK);
            AnalysisResponse body = resp.getBody();
            assertThat(body).as(f.name()).isNotNull();

            // done-when shape: a calibrated probability, ranked contributions, three similar scams
            assertThat(body.probability()).as(f.name() + " probability").isBetween(0.0, 1.0);
            assertThat(body.topContributions()).as(f.name() + " contributions").isNotEmpty();
            assertThat(body.similarScams()).as(f.name() + " similar scams").hasSize(3);

            body.stageMillis().forEach((k, v) -> stageTotals.merge(k, v, Long::sum));

            switch (f.expect()) {
                case "SCAM" -> {
                    if (f.name().equals("canonical_scam")) {
                        canonical = body;
                    }
                }
                case "LEGIT" -> legit.add(body);
                case "OBFUSCATED" -> obfuscated = body;
                default -> { }
            }
            if (sampleId == null) {
                sampleId = body.id();
            }
        }

        // The canonical scam is flagged.
        assertThat(canonical).as("canonical scam analyzed").isNotNull();
        assertThat(canonical.probability()).as("canonical scam probability").isGreaterThanOrEqualTo(0.5);

        // The three legitimate postings must NOT be flagged as scams.
        assertThat(legit).hasSize(3);
        for (AnalysisResponse r : legit) {
            assertThat(r.label()).as("legitimate posting must not be flagged").isNotEqualTo("LIKELY_SCAM");
        }

        // The obfuscated posting exercises the character n-grams (word tokens mostly vanish).
        assertThat(obfuscated).as("obfuscated posting analyzed").isNotNull();
        double[] vec = vectorizer.vectorize(fixtureText("obfuscated_earn_daily"));
        int charNonZero = 0;
        for (int i = 40_000; i < vec.length; i++) {
            if (vec[i] != 0.0) {
                charNonZero++;
            }
        }
        assertThat(charNonZero).as("char n-gram features fire on spaced obfuscation").isGreaterThan(0);

        // GET permalink round-trip returns the stored verdict.
        ResponseEntity<AnalysisResponse> got = rest.getForEntity(
                "/api/v1/analysis/" + sampleId, AnalysisResponse.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody().id()).isEqualTo(sampleId);
        assertThat(got.getBody().cached()).isTrue();

        // Re-analyzing identical text returns the cached verdict (content-hash dedup).
        ResponseEntity<AnalysisResponse> again = rest.postForEntity(
                "/api/v1/analysis", new AnalyzeRequest(fixtures.get(0).text(), fixtures.get(0).kind()),
                AnalysisResponse.class);
        assertThat(again.getBody().cached()).as("content-hash dedup").isTrue();

        printLatencyReport(latencies, stageTotals, canonical, obfuscated, legit);
    }

    private void printLatencyReport(List<Long> latencies, Map<String, Long> stageTotals,
                                    AnalysisResponse canonical, AnalysisResponse obfuscated,
                                    List<AnalysisResponse> legit) {
        List<Long> sorted = latencies.stream().sorted().toList();
        int n = sorted.size();
        long p50 = sorted.get(n / 2);
        long p95 = sorted.get((int) Math.ceil(0.95 * n) - 1);
        long max = sorted.get(n - 1);

        System.out.println("\n===== Verity — analysis over " + n + " fixtures =====");
        System.out.printf("canonical scam:  p=%.3f label=%s phrases=%d%n",
                canonical.probability(), canonical.label(), canonical.matchedPhrases().size());
        System.out.printf("obfuscated:      p=%.3f label=%s%n", obfuscated.probability(), obfuscated.label());
        System.out.printf("legit example:   p=%.3f label=%s%n", legit.get(0).probability(), legit.get(0).label());
        System.out.println("--- latency (end-to-end HTTP, ms) ---");
        System.out.printf("p50=%d  p95=%d  max=%d%n", p50, p95, max);
        System.out.println("--- mean per-stage latency (ms) ---");
        stageTotals.forEach((stage, sum) -> System.out.printf("  %-18s %6.1f%n", stage, sum / (double) n));
        if (p95 > 150) {
            System.out.println("NOTE: p95 exceeds the 150ms target — see the per-stage breakdown above "
                    + "for where the time went (before any optimization).");
        }
    }

    private String fixtureText(String name) {
        return fixtures.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow().text();
    }

    private void loadKnownScamsSeed() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM known_scams")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        int loaded = 0;
        int failed = 0;
        try (InputStream in = getClass().getResourceAsStream("/seed/known_scams_seed.sql");
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.strip();
                if (!s.startsWith("INSERT")) {
                    continue;
                }
                if (s.endsWith(";")) {
                    s = s.substring(0, s.length() - 1);
                }
                try {
                    st.executeUpdate(s);
                    loaded++;
                } catch (Exception e) {
                    failed++;
                }
            }
        }
        System.out.println("known_scams seed loaded=" + loaded + " failed=" + failed);
        assertThat(loaded).as("enough known scams to search").isGreaterThanOrEqualTo(3);
    }

    private List<Fixture> loadFixtures() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/postings.json")) {
            JsonNode arr = new ObjectMapper().readTree(in);
            List<Fixture> out = new ArrayList<>();
            for (JsonNode node : arr) {
                out.add(new Fixture(node.get("name").asText(), node.get("kind").asText(),
                        node.get("expect").asText(), node.get("text").asText()));
            }
            return out;
        }
    }

    private record Fixture(String name, String kind, String expect, String text) {}
}
