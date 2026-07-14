package com.scamshield.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the linear model's coefficients into honest, per-feature explanations. For logistic
 * regression a feature's contribution to the score is exactly {@code coefficient × tfidf} — a
 * term in the <em>log-odds</em> (not the probability). This is computed directly, never
 * approximated, which is why the served model is linear.
 */
public class Explainer {

    private final double[] coef;
    private final double intercept;

    private Explainer(double[] coef, double intercept) {
        this.coef = coef;
        this.intercept = intercept;
    }

    public static Explainer fromClasspath(String resource) throws IOException {
        try (InputStream in = Explainer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("coef JSON not found on classpath: " + resource);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode coefNode = root.get("coef");
            double[] coef = new double[coefNode.size()];
            for (int i = 0; i < coef.length; i++) {
                coef[i] = coefNode.get(i).asDouble();
            }
            return new Explainer(coef, root.get("intercept").asDouble());
        }
    }

    public double intercept() {
        return intercept;
    }

    /** Decision function f = intercept + Σ coefᵢ·tfidfᵢ (the log-odds the calibration maps). */
    public double logit(double[] tfidf) {
        double sum = intercept;
        for (int i = 0; i < tfidf.length; i++) {
            if (tfidf[i] != 0.0) {
                sum += coef[i] * tfidf[i];
            }
        }
        return sum;
    }

    /** Top-k features by |contribution| (log-odds), each named via the vectorizer. */
    public List<Contribution> topContributions(double[] tfidf, TfidfVectorizer vectorizer, int k) {
        List<Contribution> all = new ArrayList<>();
        for (int i = 0; i < tfidf.length; i++) {
            if (tfidf[i] != 0.0) {
                double contribution = coef[i] * tfidf[i];
                all.add(new Contribution(vectorizer.featureName(i), contribution, vectorizer.isCharFeature(i)));
            }
        }
        all.sort(Comparator.comparingDouble((Contribution c) -> Math.abs(c.contribution())).reversed());
        return all.size() > k ? new ArrayList<>(all.subList(0, k)) : all;
    }

    public record Contribution(String feature, double contribution, boolean charNgram) {}
}
