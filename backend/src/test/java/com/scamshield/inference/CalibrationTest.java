package com.scamshield.inference;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Verifies the calibration applied to the raw ONNX probability reproduces the served
 * probability the notebook reported — and documents why the naive formula is wrong.
 */
class CalibrationTest {

    private JsonNode fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/models/fixed_test_vector.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void plattCalibrationReproducesServedProbability() throws Exception {
        JsonNode fixture = fixture();
        double raw = fixture.get("onnx_probability_pos").asDouble();
        double servedExpected = fixture.get("served_calibrated_probability").asDouble();

        PlattCalibrator calibrator = PlattCalibrator.fromClasspath("/models/coef.json");
        double calibrated = calibrator.calibrate(raw);

        assertThat(calibrated)
                .as("calibrated probability (the value shown to users)")
                .isCloseTo(servedExpected, Offset.offset(1e-6));
    }

    @Test
    void feedingTheRawProbabilityIntoTheSigmoidIsWrong() throws Exception {
        JsonNode fixture = fixture();
        double raw = fixture.get("onnx_probability_pos").asDouble();
        double servedExpected = fixture.get("served_calibrated_probability").asDouble();

        PlattCalibrator calibrator = PlattCalibrator.fromClasspath("/models/coef.json");

        // The trap: Platt is fit on the log-odds, so plugging the raw PROBABILITY into
        // 1/(1+exp(a*x+b)) gives a badly wrong answer. This asserts the wrong path stays wrong,
        // so nobody "simplifies" calibrate() into it later.
        double wrong = 1.0 / (1.0 + Math.exp(calibrator.a() * raw + calibrator.b()));

        assertThat(wrong)
                .as("using the raw probability instead of the log-odds must NOT match")
                .isNotCloseTo(servedExpected, Offset.offset(0.05));
    }
}
