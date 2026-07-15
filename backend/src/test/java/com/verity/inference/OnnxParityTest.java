package com.verity.inference;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Guards the Python↔Java boundary. A silent preprocessing mismatch (tokenization, IDF ordering,
 * feature indexing) makes the app "work" while returning wrong predictions. This test feeds the
 * <em>exact</em> TF-IDF vector the notebook fed its ONNX model and asserts the Java ONNX runtime
 * returns the identical raw probability, to within 1e-6. Do not loosen the tolerance.
 */
class OnnxParityTest {

    private static final String MODEL = "/models/classifier.onnx";
    private static final String FIXTURE = "/models/fixed_test_vector.json";
    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);

    @Test
    void onnxRawProbabilityMatchesNotebookWithin1e6() throws Exception {
        JsonNode fixture;
        try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixed_test_vector.json on classpath").isNotNull();
            fixture = new ObjectMapper().readTree(in);
        }

        int inputDim = fixture.get("input_dim").asInt();
        double expected = fixture.get("onnx_probability_pos").asDouble();

        // Reconstruct the identical sparse TF-IDF vector as a dense float array.
        // (Casting the stored float64 values to float32 is deterministic and matches numpy's
        // astype(float32), so no rounding drift is introduced here.)
        float[] vector = new float[inputDim];
        JsonNode indices = fixture.get("nonzero_indices");
        JsonNode values = fixture.get("nonzero_values");
        for (int i = 0; i < indices.size(); i++) {
            vector[indices.get(i).asInt()] = (float) values.get(i).asDouble();
        }

        try (OnnxClassifier classifier = OnnxClassifier.fromClasspath(MODEL)) {
            assertThat(classifier.inputDim())
                    .as("model input dimension matches the fixture")
                    .isEqualTo(inputDim);

            double actual = classifier.rawProbability(vector);

            assertThat(actual)
                    .as("ONNX raw positive-class probability must equal the notebook value")
                    .isCloseTo(expected, TOLERANCE);
        }
    }
}
