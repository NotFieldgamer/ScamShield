package com.verity.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.verity.modelregistry.dto.ConfusionResponse;
import com.verity.modelregistry.dto.ModelMetricsResponse;
import com.verity.support.ClerkTestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the /model done-when: as the threshold moves, precision, recall, "real jobs blocked"
 * (false positives), and "scams let through" (false negatives) are all recomputed from the stored
 * validation predictions — not hard-coded. Seeds a tiny, hand-verifiable prediction set so the
 * exact confusion counts at each threshold can be asserted.
 */
@Testcontainers
@Import(ClerkTestAuth.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {ClerkTestAuth.ISSUER_PROP, ClerkTestAuth.SECRET_KEY_PROP})
class ModelMetricsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    // Six held-out predictions (y_true, y_score). P = 3 frauds, N = 3 legit.
    //   frauds:  0.90, 0.70, 0.40
    //   legit:   0.60, 0.20, 0.10
    @BeforeEach
    void seedPredictions() {
        Long modelId = jdbc.queryForObject(
                "SELECT id FROM model_versions WHERE active LIMIT 1", Long.class);
        jdbc.update("DELETE FROM validation_predictions");
        insert(modelId, 1, 0.90);
        insert(modelId, 1, 0.70);
        insert(modelId, 1, 0.40);
        insert(modelId, 0, 0.60);
        insert(modelId, 0, 0.20);
        insert(modelId, 0, 0.10);
    }

    private void insert(Long modelId, int yTrue, double yScore) {
        jdbc.update("INSERT INTO validation_predictions (model_version_id, y_true, y_score, split) "
                + "VALUES (?, ?, ?, 'TEST')", modelId, yTrue, yScore);
    }

    @Test
    void confusionRecomputesFromStoredPredictionsAsThresholdMoves() {
        // t = 0.35: positives are 0.90, 0.70, 0.60, 0.40 → TP=3 (frauds), FP=1 (0.60 legit), FN=0, TN=2.
        ConfusionResponse low = confusion(0.35);
        assertThat(low.truePositives()).isEqualTo(3);
        assertThat(low.falsePositives()).isEqualTo(1);
        assertThat(low.falseNegatives()).isEqualTo(0);
        assertThat(low.trueNegatives()).isEqualTo(2);
        assertThat(low.realJobsBlocked()).isEqualTo(1);      // FP, in human terms
        assertThat(low.scamsLetThrough()).isEqualTo(0);      // FN, in human terms
        assertThat(low.precision()).isCloseTo(0.75, within(1e-9));
        assertThat(low.recall()).isCloseTo(1.0, within(1e-9));

        // t = 0.50: positives are 0.90, 0.70, 0.60 → TP=2, FP=1, FN=1 (the 0.40 fraud), TN=2.
        ConfusionResponse mid = confusion(0.50);
        assertThat(mid.truePositives()).isEqualTo(2);
        assertThat(mid.falsePositives()).isEqualTo(1);
        assertThat(mid.falseNegatives()).isEqualTo(1);
        assertThat(mid.trueNegatives()).isEqualTo(2);
        assertThat(mid.precision()).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(mid.recall()).isCloseTo(2.0 / 3.0, within(1e-9));

        // t = 0.65: positives are 0.90, 0.70 → TP=2, FP=0, FN=1, TN=3.
        ConfusionResponse high = confusion(0.65);
        assertThat(high.truePositives()).isEqualTo(2);
        assertThat(high.falsePositives()).isEqualTo(0);
        assertThat(high.falseNegatives()).isEqualTo(1);
        assertThat(high.trueNegatives()).isEqualTo(3);
        assertThat(high.precision()).isCloseTo(1.0, within(1e-9));
        assertThat(high.recall()).isCloseTo(2.0 / 3.0, within(1e-9));

        // The whole point of the slider: all four numbers actually change as it moves.
        assertThat(low.falsePositives()).isNotEqualTo(high.falsePositives());  // 1 vs 0
        assertThat(low.falseNegatives()).isNotEqualTo(mid.falseNegatives());   // 0 vs 1
        assertThat(low.precision()).isNotEqualTo(high.precision());
        assertThat(mid.recall()).isNotEqualTo(low.recall());
    }

    @Test
    void metricsSummaryIsDerivedFromStoredPredictions() {
        ModelMetricsResponse m = rest.getForObject(
                "/api/v1/models/active/metrics", ModelMetricsResponse.class);

        assertThat(m.hasPredictions()).isTrue();
        assertThat(m.total()).isEqualTo(6);
        assertThat(m.positives()).isEqualTo(3);
        assertThat(m.negatives()).isEqualTo(3);
        assertThat(m.noSkillFloor()).isCloseTo(0.5, within(1e-9)); // prevalence 3/6
        assertThat(m.pr()).isNotEmpty();
        assertThat(m.roc()).isNotEmpty();
        assertThat(m.calibration()).isNotEmpty();
        assertThat(m.grid()).hasSize(101); // thresholds 0.00..1.00

        // Operating point = highest recall at precision >= 0.90. Here that is threshold 0.70
        // (precision 1.0, recall 2/3); loosening further to 0.60 drops precision to 2/3.
        assertThat(m.operating()).isNotNull();
        assertThat(m.operating().precision()).isGreaterThanOrEqualTo(0.90);
        assertThat(m.operating().recall()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void reportsHonestlyWhenNoPredictionsAreLoaded() {
        jdbc.update("DELETE FROM validation_predictions");
        ModelMetricsResponse m = rest.getForObject(
                "/api/v1/models/active/metrics", ModelMetricsResponse.class);
        assertThat(m.hasPredictions()).isFalse();
        assertThat(m.total()).isZero();
        assertThat(m.prAuc()).isNull();
        assertThat(m.grid()).isEmpty();

        // The confusion endpoint must be honest too: no predictions means no matrix — never a
        // fabricated precision of 1.0 computed from zero examples.
        ConfusionResponse c = confusion(0.5);
        assertThat(c.hasPredictions()).isFalse();
        assertThat(c.total()).isZero();
        assertThat(c.precision()).isZero();
        assertThat(c.recall()).isZero();
    }

    private ConfusionResponse confusion(double threshold) {
        return rest.getForObject(
                "/api/v1/models/active/confusion?threshold=" + threshold, ConfusionResponse.class);
    }
}
