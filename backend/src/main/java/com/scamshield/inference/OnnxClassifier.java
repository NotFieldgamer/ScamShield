package com.scamshield.inference;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * In-process ONNX inference for the served classifier.
 *
 * <p>The exported model is the <em>raw</em> logistic regression: it takes the TF-IDF feature
 * vector and returns the uncalibrated positive-class probability. Calibration is a separate
 * step ({@link PlattCalibrator}) applied to this raw value.
 */
public class OnnxClassifier implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final int inputDim;

    public OnnxClassifier(byte[] modelBytes) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        this.inputName = session.getInputNames().iterator().next();

        NodeInfo node = session.getInputInfo().get(inputName);
        long[] shape = ((TensorInfo) node.getInfo()).getShape();
        this.inputDim = (int) shape[shape.length - 1];
    }

    public static OnnxClassifier fromClasspath(String resource) throws IOException, OrtException {
        try (InputStream in = OnnxClassifier.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("ONNX model not found on classpath: " + resource);
            }
            return new OnnxClassifier(in.readAllBytes());
        }
    }

    public int inputDim() {
        return inputDim;
    }

    /**
     * Positive-class RAW (uncalibrated) probability for a single TF-IDF vector.
     *
     * @param tfidf dense feature vector; length must equal {@link #inputDim()}
     */
    public double rawProbability(float[] tfidf) throws OrtException {
        if (tfidf.length != inputDim) {
            throw new IllegalArgumentException(
                    "feature vector length " + tfidf.length + " != model input dim " + inputDim);
        }
        float[][] batch = {tfidf};
        try (OnnxTensor input = OnnxTensor.createTensor(env, batch);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {
            return positiveProbability(result);
        }
    }

    /** skl2onnx (zipmap disabled) emits an (N,2) float "probabilities" tensor; column 1 = positive. */
    private static double positiveProbability(OrtSession.Result result) throws OrtException {
        for (Map.Entry<String, OnnxValue> entry : result) {
            if (entry.getValue() instanceof OnnxTensor tensor
                    && tensor.getValue() instanceof float[][] probs
                    && probs.length > 0 && probs[0].length == 2) {
                return probs[0][1];
            }
        }
        throw new OrtException("no (N,2) float probability output found in ONNX result");
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
