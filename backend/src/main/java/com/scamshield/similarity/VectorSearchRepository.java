package com.scamshield.similarity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * pgvector cosine search over {@code known_scams} and storage of posting embeddings.
 * Uses raw SQL (JdbcTemplate) rather than JPA because the {@code vector(384)} type has no clean
 * Hibernate mapping and the query path is a hand-written {@code <=>} cosine ordering.
 */
@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbc;

    public VectorSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Three nearest confirmed scams to a fresh query embedding (cosine similarity). */
    public List<SimilarScam> nearest(float[] embedding, int k) {
        String vec = toVectorLiteral(embedding);
        return jdbc.query(
                "SELECT id, text, source, (embedding <=> CAST(? AS vector)) AS distance "
                        + "FROM known_scams ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                (rs, i) -> new SimilarScam(rs.getLong("id"), rs.getString("text"),
                        rs.getString("source"), 1.0 - rs.getDouble("distance")),
                vec, vec, k);
    }

    /** Nearest scams using a posting's already-stored embedding (cache-hit path; no re-embedding). */
    public List<SimilarScam> nearestForPosting(UUID postingId, int k) {
        return jdbc.query(
                "SELECT ks.id, ks.text, ks.source, (ks.embedding <=> pe.embedding) AS distance "
                        + "FROM known_scams ks, posting_embeddings pe "
                        + "WHERE pe.posting_id = ? ORDER BY ks.embedding <=> pe.embedding LIMIT ?",
                (rs, i) -> new SimilarScam(rs.getLong("id"), rs.getString("text"),
                        rs.getString("source"), 1.0 - rs.getDouble("distance")),
                postingId, k);
    }

    public void savePostingEmbedding(UUID postingId, float[] embedding) {
        jdbc.update(
                "INSERT INTO posting_embeddings (posting_id, embedding) VALUES (?, CAST(? AS vector)) "
                        + "ON CONFLICT (posting_id) DO NOTHING",
                postingId, toVectorLiteral(embedding));
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public record SimilarScam(long id, String text, String source, double similarity) {}
}
