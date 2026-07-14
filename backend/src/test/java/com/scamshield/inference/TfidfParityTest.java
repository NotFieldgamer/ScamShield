package com.scamshield.inference;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The test the whole preprocessing layer exists for: the Java TF-IDF must reproduce the
 * scikit-learn vector for the fixed text — same non-zero indices, same values to 1e-6.
 * A tokenisation or IDF-ordering bug shows up here as a missing/extra index or a wrong value.
 */
class TfidfParityTest {

    @Test
    void javaTfidfMatchesSklearnVectorForFixedText() throws Exception {
        JsonNode fixture;
        try (InputStream in = getClass().getResourceAsStream("/models/fixed_test_vector.json")) {
            assertThat(in).as("fixture on classpath").isNotNull();
            fixture = new ObjectMapper().readTree(in);
        }
        String text = fixture.get("fixed_text").asText();

        Map<Integer, Double> expected = new HashMap<>();
        JsonNode idx = fixture.get("nonzero_indices");
        JsonNode val = fixture.get("nonzero_values");
        for (int i = 0; i < idx.size(); i++) {
            expected.put(idx.get(i).asInt(), val.get(i).asDouble());
        }

        TfidfVectorizer vectorizer = TfidfVectorizer.fromClasspath("/models/vocab.json");
        double[] actual = vectorizer.vectorize(text);

        Map<Integer, Double> actualNonZero = new HashMap<>();
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != 0.0) {
                actualNonZero.put(i, actual[i]);
            }
        }

        // Exact same set of active features (tokenisation + vocab mapping correct).
        assertThat(new TreeSet<>(actualNonZero.keySet()))
                .as("active feature indices must match sklearn exactly")
                .isEqualTo(new TreeSet<>(expected.keySet()));

        // Same values (sublinear TF, IDF, per-block L2 norm correct).
        double maxDiff = 0.0;
        for (Map.Entry<Integer, Double> e : expected.entrySet()) {
            double diff = Math.abs(actualNonZero.getOrDefault(e.getKey(), 0.0) - e.getValue());
            maxDiff = Math.max(maxDiff, diff);
        }
        assertThat(maxDiff)
                .as("max per-feature TF-IDF difference vs sklearn")
                .isLessThan(1e-6);
    }
}
