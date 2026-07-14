package com.scamshield.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java reimplementation of the served scikit-learn TF-IDF, exact enough to feed the ONNX
 * classifier and to name feature contributions. It replicates two independently L2-normalised
 * blocks — word (1–2)-grams and {@code char_wb} (3–5)-grams — concatenated as {@code [word || char]},
 * matching {@code FeatureUnion([TfidfVectorizer(word), TfidfVectorizer(char_wb)])}.
 *
 * <p>Preprocessing, tokenisation, sublinear TF, IDF and normalisation all mirror sklearn; the
 * {@code OnnxParityTest} / {@code TfidfParityTest} fixtures guard the reproduction. A silent
 * mismatch here is the failure this whole layer is built to prevent.
 */
public class TfidfVectorizer {

    // sklearn default token_pattern r"(?u)\b\w\w+\b": runs of 2+ (unicode) word characters.
    private static final Pattern WORD_TOKEN = Pattern.compile("\\w\\w+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int nWord;
    private final int nFeatures;
    private final Map<String, Integer> wordVocab;
    private final Map<String, Integer> charVocab;
    private final double[] wordIdf;
    private final double[] charIdf;
    private final String[] featureNames; // index -> term, for contribution naming

    private TfidfVectorizer(JsonNode root) {
        this.nWord = root.get("n_word").asInt();
        int nChar = root.get("n_char").asInt();
        this.nFeatures = root.get("n_features").asInt();

        this.wordVocab = new HashMap<>(nWord * 2);
        this.charVocab = new HashMap<>(nChar * 2);
        this.wordIdf = new double[nWord];
        this.charIdf = new double[nChar];
        this.featureNames = new String[nFeatures];

        loadBlock(root.get("word"), wordVocab, wordIdf, 0);
        loadBlock(root.get("char"), charVocab, charIdf, nWord);
    }

    private void loadBlock(JsonNode block, Map<String, Integer> vocab, double[] idf, int offset) {
        block.get("vocabulary").properties().forEach(e -> {
            int idx = e.getValue().asInt();
            vocab.put(e.getKey(), idx);
            featureNames[offset + idx] = e.getKey();
        });
        JsonNode idfNode = block.get("idf");
        for (int i = 0; i < idfNode.size(); i++) {
            idf[i] = idfNode.get(i).asDouble();
        }
    }

    public static TfidfVectorizer fromClasspath(String resource) throws IOException {
        try (InputStream in = TfidfVectorizer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("vocab JSON not found on classpath: " + resource);
            }
            return new TfidfVectorizer(new ObjectMapper().readTree(in));
        }
    }

    public int nFeatures() {
        return nFeatures;
    }

    /** Human-readable term for a feature index (word or char n-gram), or null if out of range. */
    public String featureName(int index) {
        return index >= 0 && index < nFeatures ? featureNames[index] : null;
    }

    public boolean isCharFeature(int index) {
        return index >= nWord;
    }

    /** Whether a term (by name) belongs to the char n-gram block — used to classify stored features. */
    public boolean isCharTerm(String term) {
        return charVocab.containsKey(term) && !wordVocab.containsKey(term);
    }

    /** Full 80k-dim TF-IDF vector in double precision (cast to float only when feeding ONNX). */
    public double[] vectorize(String text) {
        String pre = preprocess(text);
        double[] vec = new double[nFeatures];

        List<String> tokens = wordTokens(pre);
        accumulate(vec, wordNgrams(tokens), wordVocab, wordIdf, 0);
        accumulate(vec, charWbNgrams(pre), charVocab, charIdf, nWord);
        return vec;
    }

    public static float[] toFloat(double[] v) {
        float[] f = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            f[i] = (float) v[i];
        }
        return f;
    }

    // --- one TF-IDF block: sublinear TF, IDF weight, then independent L2 normalisation ---
    private static void accumulate(double[] vec, List<String> grams, Map<String, Integer> vocab,
                                   double[] idf, int offset) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String gram : grams) {
            Integer idx = vocab.get(gram);
            if (idx != null) {
                counts.merge(idx, 1, Integer::sum);
            }
        }
        Map<Integer, Double> weights = new HashMap<>(counts.size() * 2);
        double sumSq = 0.0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            double tf = 1.0 + Math.log(e.getValue()); // sublinear_tf
            double w = tf * idf[e.getKey()];
            weights.put(e.getKey(), w);
            sumSq += w * w;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 0.0) {
            for (Map.Entry<Integer, Double> e : weights.entrySet()) {
                vec[offset + e.getKey()] = e.getValue() / norm;
            }
        }
    }

    // sklearn preprocessing: lowercase first, then strip_accents='unicode' (NFKD + drop marks).
    private static String preprocess(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String nfkd = Normalizer.normalize(lower, Normalizer.Form.NFKD);
        return COMBINING.matcher(nfkd).replaceAll("");
    }

    private static List<String> wordTokens(String pre) {
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD_TOKEN.matcher(pre);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    // word ngram_range (1,2): unigrams + space-joined consecutive bigrams.
    private static List<String> wordNgrams(List<String> tokens) {
        List<String> grams = new ArrayList<>(tokens);
        for (int i = 0; i + 2 <= tokens.size(); i++) {
            grams.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return grams;
    }

    // char_wb ngram_range (3,5): per whitespace-split word, pad " "+w+" ", faithful to sklearn's
    // loop including "count a short word only once" (the offset==0 break).
    private static List<String> charWbNgrams(String pre) {
        List<String> grams = new ArrayList<>();
        for (String w : WHITESPACE.split(pre.trim())) {
            if (w.isEmpty()) {
                continue;
            }
            String pw = " " + w + " ";
            int len = pw.length();
            for (int n = 3; n <= 5; n++) {
                int offset = 0;
                grams.add(pw.substring(offset, Math.min(offset + n, len)));
                while (offset + n < len) {
                    offset++;
                    grams.add(pw.substring(offset, offset + n));
                }
                if (offset == 0) {
                    break; // short word (len < n): counted once, larger n add nothing new
                }
            }
        }
        return grams;
    }
}
