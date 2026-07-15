package com.verity.similarity;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-process sentence embedding with all-MiniLM-L6-v2 (ONNX), matching the offline
 * sentence-transformers pipeline: tokenize → transformer → <b>mean pooling over the attention
 * mask</b> → 384-dim vector (unnormalized, as the seed embeddings were produced). No Python at
 * request time. This is the most expensive stage of the pipeline (a CPU transformer forward pass).
 */
public class EmbeddingService implements AutoCloseable {

    private static final int MAX_TOKENS = 256; // MiniLM's trained max sequence length
    private static final int DIM = 384;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> inputNames;

    /**
     * Loads the model from a <em>file path</em> on purpose. ONNX Runtime reads the weights straight
     * into native memory, so the ~90MB never occupies the Java heap — and is never transiently held
     * twice (once as a heap {@code byte[]}, once natively), which is what a 512MB container cannot
     * afford. Never re-introduce a {@code byte[]}-based constructor for this model.
     */
    public EmbeddingService(Path onnxModel, Path tokenizerJson) throws OrtException, IOException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(onnxModel.toString(), leanOptions());
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerJson);
        this.inputNames = session.getInputNames();
    }

    /**
     * Session options tuned for a 512MB / 0.1-CPU host. Single-threaded (there is no parallelism to
     * win on a tenth of a core) and with the CPU memory arena disabled — the arena pre-reserves large
     * native blocks and never returns them, so turning it off trades a little speed for markedly lower
     * resident memory, which is what keeps this transformer inside the container's RAM limit.
     */
    static OrtSession.SessionOptions leanOptions() throws OrtException {
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setInterOpNumThreads(1);
        opts.setIntraOpNumThreads(1);
        opts.setMemoryPatternOptimization(false);
        opts.setCPUArenaAllocator(false);
        return opts;
    }

    public int dimension() {
        return DIM;
    }

    public float[] embed(String text) throws OrtException {
        Encoding encoding = tokenizer.encode(text);
        long[] ids = truncate(encoding.getIds());
        long[] mask = truncate(encoding.getAttentionMask());
        long[] types = truncate(encoding.getTypeIds());

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, new long[][]{ids}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{mask}));
            if (inputNames.contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{types}));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] hidden = (float[][][]) lastHiddenState(result).getValue(); // [1, L, 384]
                return meanPool(hidden[0], mask);
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private OnnxValue lastHiddenState(OrtSession.Result result) {
        return result.get("last_hidden_state").orElseGet(() -> result.iterator().next().getValue());
    }

    private static float[] meanPool(float[][] tokenEmbeddings, long[] mask) {
        float[] pooled = new float[DIM];
        double count = 0;
        for (int t = 0; t < tokenEmbeddings.length; t++) {
            if (mask[t] == 0) {
                continue;
            }
            count++;
            float[] emb = tokenEmbeddings[t];
            for (int d = 0; d < DIM; d++) {
                pooled[d] += emb[d];
            }
        }
        if (count > 0) {
            for (int d = 0; d < DIM; d++) {
                pooled[d] /= (float) count;
            }
        }
        return pooled;
    }

    private static long[] truncate(long[] values) {
        return values.length <= MAX_TOKENS ? values : Arrays.copyOf(values, MAX_TOKENS);
    }

    @Override
    public void close() throws OrtException {
        session.close();
        tokenizer.close();
    }
}
