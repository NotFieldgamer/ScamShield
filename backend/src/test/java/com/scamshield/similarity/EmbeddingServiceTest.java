package com.scamshield.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;

/**
 * The MiniLM model is loaded from a <em>file path</em> so ONNX Runtime reads its ~90MB of weights
 * straight into native memory and they never occupy the Java heap (a heap {@code byte[]} load
 * OOM'd the 512MB deploy container). This test pins that contract and the lean session options:
 * loading by path must still produce a correct 384-dim embedding.
 *
 * <p>The model is gitignored; the test skips when it is absent. CI fetches it before running tests.
 */
class EmbeddingServiceTest {

    @Test
    void loadsModelFromPathAndEmbeds() throws Exception {
        Path model = classpathToTemp("/models/minilm.onnx", ".onnx");
        Path tokenizer = classpathToTemp("/models/minilm_tokenizer.json", ".json");
        assumeTrue(model != null && tokenizer != null, "MiniLM model not on classpath — skipping");

        try (EmbeddingService service = new EmbeddingService(model, tokenizer)) {
            assertThat(service.dimension()).isEqualTo(384);

            float[] vector = service.embed("Earn daily from home — pay a small processing fee to start.");
            assertThat(vector).as("embedding is the model's 384-dim output").hasSize(384);
            assertThat(hasNonZero(vector)).as("embedding must not be all zeros").isTrue();

            // The same text must embed deterministically — single-threaded, arena-free options
            // change memory behaviour, never the arithmetic.
            float[] again = service.embed("Earn daily from home — pay a small processing fee to start.");
            assertThat(again).isEqualTo(vector);
        }
    }

    private static boolean hasNonZero(float[] values) {
        for (float v : values) {
            if (v != 0f) {
                return true;
            }
        }
        return false;
    }

    /** Streams a classpath resource to a temp file, or returns null when it is not present. */
    private static Path classpathToTemp(String resource, String suffix) throws Exception {
        try (InputStream in = EmbeddingServiceTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Path temp = Files.createTempFile("scamshield-test-", suffix);
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }
}
