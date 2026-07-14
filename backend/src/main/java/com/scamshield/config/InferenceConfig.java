package com.scamshield.config;

import com.scamshield.inference.Explainer;
import com.scamshield.inference.OnnxClassifier;
import com.scamshield.inference.PlattCalibrator;
import com.scamshield.inference.SalaryPlausibility;
import com.scamshield.inference.TfidfVectorizer;
import com.scamshield.matching.TyposquatDetector;
import com.scamshield.similarity.EmbeddingService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the offline ML artifacts once at startup and wires the (framework-free) inference
 * components as singleton beans. Artifacts live on the classpath under {@code /models/}.
 */
@Configuration
public class InferenceConfig {

    @Bean
    public TfidfVectorizer tfidfVectorizer() throws IOException {
        return TfidfVectorizer.fromClasspath("/models/vocab.json");
    }

    @Bean(destroyMethod = "close")
    public OnnxClassifier onnxClassifier() throws Exception {
        return OnnxClassifier.fromClasspath("/models/classifier.onnx");
    }

    @Bean
    public PlattCalibrator plattCalibrator() throws IOException {
        return PlattCalibrator.fromClasspath("/models/coef.json");
    }

    @Bean
    public Explainer explainer() throws IOException {
        return Explainer.fromClasspath("/models/coef.json");
    }

    @Bean
    public SalaryPlausibility salaryPlausibility() throws IOException {
        return SalaryPlausibility.fromClasspath("/models/salary_params.json");
    }

    @Bean
    public TyposquatDetector typosquatDetector() {
        return new TyposquatDetector();
    }

    @Bean(destroyMethod = "close")
    public EmbeddingService embeddingService(
            @Value("${app.embedding.model-path:/app/models/minilm.onnx}") String modelPath)
            throws Exception {
        byte[] onnx = readEmbeddingModel(modelPath);
        Path tokenizer = copyToTemp("/models/minilm_tokenizer.json");
        return new EmbeddingService(onnx, tokenizer);
    }

    /**
     * The MiniLM model (~90MB) is not committed to the repo. In the Docker image it is downloaded
     * at build time (from {@code EMBEDDING_MODEL_URL}) to {@code EMBEDDING_MODEL_PATH}, so read it
     * from the filesystem there. For local dev and tests, fall back to a copy on the classpath if
     * one is present — it is gitignored, so this fallback never depends on a committed model.
     */
    private static byte[] readEmbeddingModel(String modelPath) throws IOException {
        Path file = Path.of(modelPath);
        if (Files.isRegularFile(file)) {
            return Files.readAllBytes(file);
        }
        try (InputStream in = InferenceConfig.class.getResourceAsStream("/models/minilm.onnx")) {
            if (in == null) {
                throw new IOException(
                        "MiniLM embedding model not found at '" + modelPath + "' (EMBEDDING_MODEL_PATH)"
                        + " and not on the classpath. In the Docker image it is fetched at build time"
                        + " from EMBEDDING_MODEL_URL — set that Space Variable, or point"
                        + " EMBEDDING_MODEL_PATH at a local model file.");
            }
            return in.readAllBytes();
        }
    }

    // djl's tokenizer needs a filesystem path; extract the classpath resource to a temp file.
    private static Path copyToTemp(String resource) throws IOException {
        try (InputStream in = InferenceConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing tokenizer on classpath: " + resource);
            }
            Path temp = Files.createTempFile("scamshield-tokenizer", ".json");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }
}
