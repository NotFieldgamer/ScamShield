package com.scamshield.analysis;

import com.scamshield.inference.Explainer.Contribution;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence for postings, verdicts, and their feature contributions (raw SQL). */
@Repository
public class AnalysisRepository {

    private final JdbcTemplate jdbc;

    public AnalysisRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ModelVersionInfo activeModel() {
        return jdbc.query(
                "SELECT id, name, version FROM model_versions WHERE active "
                        + "ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> new ModelVersionInfo(rs.getLong("id"), rs.getString("name"), rs.getString("version")))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no active model_versions row seeded"));
    }

    // Every verdict query joins model_versions so the response can name the model that produced it.
    private static final String VERDICT_SELECT =
            "SELECT v.id vid, v.posting_id pid, p.raw_text, p.source, v.probability, v.label, "
                    + "v.latency_ms, mv.name model_name, mv.version model_version "
                    + "FROM verdicts v JOIN postings p ON p.id = v.posting_id "
                    + "JOIN model_versions mv ON mv.id = v.model_version_id ";

    public Optional<StoredVerdict> findByContentHash(String contentHash) {
        return jdbc.query(
                VERDICT_SELECT + "WHERE p.content_hash = ? ORDER BY v.created_at DESC LIMIT 1",
                AnalysisRepository::mapVerdict, contentHash).stream().findFirst();
    }

    public Optional<StoredVerdict> findVerdictById(UUID verdictId) {
        return jdbc.query(
                VERDICT_SELECT + "WHERE v.id = ?",
                AnalysisRepository::mapVerdict, verdictId).stream().findFirst();
    }

    public UUID insertPosting(Long userId, String rawText, String source, String contentHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO postings (id, user_id, raw_text, source, content_hash) VALUES (?, ?, ?, ?, ?)",
                id, userId, rawText, source, contentHash);
        return id;
    }

    public UUID insertVerdict(UUID postingId, long modelVersionId, double probability,
                              String label, int latencyMs) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO verdicts (id, posting_id, model_version_id, probability, label, latency_ms) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, postingId, modelVersionId, probability, label, latencyMs);
        return id;
    }

    public void insertFeatures(UUID verdictId, List<Contribution> contributions) {
        jdbc.batchUpdate(
                "INSERT INTO verdict_features (verdict_id, feature_name, contribution) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Contribution c = contributions.get(i);
                        ps.setObject(1, verdictId);
                        ps.setString(2, c.feature());
                        ps.setDouble(3, c.contribution());
                    }

                    @Override
                    public int getBatchSize() {
                        return contributions.size();
                    }
                });
    }

    public List<StoredFeature> findFeatures(UUID verdictId) {
        return jdbc.query(
                "SELECT feature_name, contribution FROM verdict_features "
                        + "WHERE verdict_id = ? ORDER BY abs(contribution) DESC",
                (rs, i) -> new StoredFeature(rs.getString("feature_name"), rs.getDouble("contribution")),
                verdictId);
    }

    private static StoredVerdict mapVerdict(java.sql.ResultSet rs, int rowNum) throws SQLException {
        return new StoredVerdict(
                (UUID) rs.getObject("vid"),
                (UUID) rs.getObject("pid"),
                rs.getString("raw_text"),
                rs.getString("source"),
                rs.getDouble("probability"),
                rs.getString("label"),
                rs.getInt("latency_ms"),
                rs.getString("model_name"),
                rs.getString("model_version"));
    }

    public record StoredVerdict(UUID verdictId, UUID postingId, String rawText, String source,
                                double probability, String label, int latencyMs,
                                String modelName, String modelVersion) {}

    public record StoredFeature(String featureName, double contribution) {}

    public record ModelVersionInfo(long id, String name, String version) {}
}
