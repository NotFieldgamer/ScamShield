package com.verity.trends;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregates the scam-driving feature contributions persisted in {@code verdict_features}. Counts
 * come straight from the table — nothing is estimated. Only features that pushed a verdict toward
 * "scam" (positive log-odds contribution) on a {@code LIKELY_SCAM} verdict are counted, so the
 * result is "patterns driving fraud calls", not noise.
 */
@Repository
public class TrendsRepository {

    private final JdbcTemplate jdbc;

    public TrendsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FeatureCount> aggregate(Instant from, Instant to) {
        return jdbc.query(
                "SELECT vf.feature_name, count(*) AS c, avg(vf.contribution) AS avg_contrib "
                        + "FROM verdict_features vf JOIN verdicts v ON v.id = vf.verdict_id "
                        + "WHERE v.created_at >= ? AND v.created_at < ? "
                        + "AND v.label = 'LIKELY_SCAM' AND vf.contribution > 0 "
                        + "GROUP BY vf.feature_name",
                (rs, i) -> new FeatureCount(
                        rs.getString("feature_name"),
                        rs.getLong("c"),
                        rs.getDouble("avg_contrib")),
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
    }

    public record FeatureCount(String featureName, long count, double avgContribution) {}
}
