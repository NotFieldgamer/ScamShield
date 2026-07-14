package com.scamshield.modelregistry;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read access to the active model and its stored held-out predictions. Everything the /model
 * page shows is derived from {@code validation_predictions} — this repository only fetches the
 * raw (y_true, y_score) rows; the arithmetic lives in {@link ModelMetricsService}.
 */
@Repository
public class ModelMetricsRepository {

    private final JdbcTemplate jdbc;

    public ModelMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ActiveModel activeModel() {
        return jdbc.query(
                "SELECT id, name, version FROM model_versions WHERE active "
                        + "ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> new ActiveModel(rs.getLong("id"), rs.getString("name"), rs.getString("version")))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no active model_versions row seeded"));
    }

    /** The held-out predictions for a model version, sorted by score descending for the sweep. */
    public Predictions predictionsFor(long modelVersionId) {
        List<double[]> rows = jdbc.query(
                "SELECT y_true, y_score FROM validation_predictions "
                        + "WHERE model_version_id = ? AND split = 'TEST' ORDER BY y_score DESC, id",
                (rs, i) -> new double[] {rs.getInt("y_true"), rs.getDouble("y_score")},
                modelVersionId);
        int n = rows.size();
        int[] yTrue = new int[n];
        double[] yScore = new double[n];
        for (int i = 0; i < n; i++) {
            yTrue[i] = (int) rows.get(i)[0];
            yScore[i] = rows.get(i)[1];
        }
        return new Predictions(yTrue, yScore);
    }

    public record ActiveModel(long id, String name, String version) {}

    /** Parallel arrays, index-aligned, sorted by {@code yScore} descending. */
    public record Predictions(int[] yTrue, double[] yScore) {
        public int size() {
            return yTrue.length;
        }
    }
}
