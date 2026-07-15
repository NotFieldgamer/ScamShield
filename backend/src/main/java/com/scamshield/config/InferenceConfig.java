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
        Path tokenizer = copyToTemp("/models/minilm_tokenizer.json", ".json");
        return new EmbeddingService(resolveEmbeddingModel(modelPath), tokenizer);
    }

    /**
     * Resolves the MiniLM model (~90MB, not committed) to a file path — never to a heap array.
     * ONNX Runtime loads a path straight into native memory; reading the model into a
     * {@code byte[]} first would put 90MB on the heap and briefly hold it twice (heap + native),
     * which does not fit a 512MB container.
     *
     * <p>In the Docker image the model is downloaded at build time (from {@code EMBEDDING_MODEL_URL})
     * to {@code EMBEDDING_MODEL_PATH}, so it is already a file. For local dev and tests it may only
     * exist on the classpath; that copy is streamed to a temp file (an 8KB buffer, not the whole
     * model), so this path stays heap-light too.
     */
    private static Path resolveEmbeddingModel(String modelPath) throws IOException {
        Path file = Path.of(modelPath);
        if (Files.isRegularFile(file)) {
            return file;
        }
        if (InferenceConfig.class.getResource("/models/minilm.onnx") == null) {
            throw new IOException(
                    "MiniLM embedding model not found at '" + modelPath + "' (EMBEDDING_MODEL_PATH)"
                    + " and not on the classpath. In the Docker image it is fetched at build time"
                    + " from EMBEDDING_MODEL_URL — set that build variable, or point"
                    + " EMBEDDING_MODEL_PATH at a local model file.");
        }
        return copyToTemp("/models/minilm.onnx", ".onnx");
    }

    /**
     * Extracts a classpath resource to a temp file (djl's tokenizer and ONNX Runtime both want a
     * path). {@link Files#copy} streams through a small buffer, so even the 90MB model never lands
     * on the heap in one piece.
     */
    private static Path copyToTemp(String resource, String suffix) throws IOException {
        try (InputStream in = InferenceConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource on classpath: " + resource);
            }
            Path temp = Files.createTempFile("scamshield-", suffix);
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }
}
