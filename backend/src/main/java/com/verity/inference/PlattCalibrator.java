package com.verity.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/**
 * Platt/sigmoid calibration applied <em>after</em> the linear model, mapping the raw
 * positive-class probability to the calibrated probability shown to users.
 *
 * <p><strong>Important:</strong> the calibration was fit on the model's decision function
 * (the log-odds {@code f}), not on the raw probability. Because the ONNX model outputs
 * {@code raw = sigmoid(f)}, we recover {@code f = ln(raw / (1 - raw))} and apply
 * {@code calibrated = 1 / (1 + exp(A*f + B))}. Feeding the raw <em>probability</em> straight
 * into that sigmoid is wrong — for the fixed test vector it yields ~0.61 instead of ~0.986.
 */
public class PlattCalibrator {

    private final double a;
    private final double b;

    public PlattCalibrator(double a, double b) {
        this.a = a;
        this.b = b;
    }

    /** Reads the {@code calibration} block ({@code a}, {@code b}) from an exported coef JSON. */
    public static PlattCalibrator fromClasspath(String resource) throws IOException {
        try (InputStream in = PlattCalibrator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("calibration JSON not found on classpath: " + resource);
            }
            JsonNode calibration = new ObjectMapper().readTree(in).get("calibration");
            if (calibration == null) {
                throw new IOException("no 'calibration' block in " + resource);
            }
            return new PlattCalibrator(calibration.get("a").asDouble(), calibration.get("b").asDouble());
        }
    }

    public double a() {
        return a;
    }

    public double b() {
        return b;
    }

    /**
     * @param rawProbability the raw positive-class probability from {@link OnnxClassifier}
     * @return the calibrated positive-class probability shown to users
     */
    public double calibrate(double rawProbability) {
        double f = Math.log(rawProbability / (1.0 - rawProbability)); // decision function (log-odds)
        return 1.0 / (1.0 + Math.exp(a * f + b));
    }
}
