package com.scamshield.campaign;

import com.scamshield.campaign.dto.CampaignDetail;
import com.scamshield.campaign.dto.CampaignDetail.Member;
import com.scamshield.campaign.dto.CampaignSummary;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads posting embeddings, finds near-duplicate edges via pgvector cosine distance, and persists
 * the Union-Find clustering into {@code campaigns} / {@code campaign_members}.
 */
@Repository
public class CampaignRepository {

    private static final int SNIPPET = 140;

    private final JdbcTemplate jdbc;

    public CampaignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int embeddingCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM posting_embeddings", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * The clustering nodes: the {@code postingLimit} most recently embedded postings. Bounding the
     * node set keeps the near-duplicate self-join from becoming an unbounded O(n²) scan as postings
     * accumulate — clustering is over recent activity, which is where reposted campaigns live.
     */
    public List<PostingInfo> postings(int postingLimit) {
        return jdbc.query(
                "SELECT p.id, left(p.raw_text, " + SNIPPET + ") AS snippet, e.created_at "
                        + "FROM postings p JOIN posting_embeddings e ON e.posting_id = p.id "
                        + "ORDER BY e.created_at DESC LIMIT ?",
                (rs, i) -> new PostingInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("snippet"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                postingLimit);
    }

    /**
     * Near-duplicate posting pairs within the {@code postingLimit} most recent embeddings: cosine
     * distance below {@code maxDistance}. The {@code recent} CTE bounds the self-join so its cost is
     * O(postingLimit²), not O(total²); {@code a < b} yields each unordered pair once; the ORDER BY
     * makes the (capped) edge set deterministic instead of whatever the planner returns first.
     * {@code <=>} is pgvector's cosine-distance operator.
     */
    public List<Edge> nearDuplicateEdges(double maxDistance, int postingLimit, int edgeLimit) {
        return jdbc.query(
                "WITH recent AS ("
                        + "  SELECT posting_id, embedding FROM posting_embeddings "
                        + "  ORDER BY created_at DESC LIMIT ?) "
                        + "SELECT a.posting_id AS a, b.posting_id AS b "
                        + "FROM recent a JOIN recent b ON a.posting_id < b.posting_id "
                        + "WHERE (a.embedding <=> b.embedding) < ? "
                        + "ORDER BY a.posting_id, b.posting_id "
                        + "LIMIT ?",
                (rs, i) -> new Edge(rs.getObject("a", UUID.class), rs.getObject("b", UUID.class)),
                postingLimit, maxDistance, edgeLimit);
    }

    /** Rebuild the campaign tables from a fresh clustering. Old clusters are discarded. */
    @Transactional
    public int rebuild(List<ClusterWrite> clusters) {
        jdbc.update("DELETE FROM campaign_members");
        jdbc.update("DELETE FROM campaigns");
        int written = 0;
        for (ClusterWrite cluster : clusters) {
            Long campaignId = jdbc.queryForObject(
                    "INSERT INTO campaigns (label, root_posting_id, member_count) VALUES (?, ?, ?) "
                            + "RETURNING id",
                    Long.class, cluster.label(), cluster.rootPostingId(), cluster.memberIds().size());
            jdbc.batchUpdate(
                    "INSERT INTO campaign_members (campaign_id, posting_id) VALUES (?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setLong(1, campaignId);
                            ps.setObject(2, cluster.memberIds().get(i));
                        }

                        @Override
                        public int getBatchSize() {
                            return cluster.memberIds().size();
                        }
                    });
            written++;
        }
        return written;
    }

    public List<CampaignSummary> list() {
        return jdbc.query(
                "SELECT id, label, root_posting_id, member_count, created_at FROM campaigns "
                        + "ORDER BY member_count DESC, created_at DESC",
                (rs, i) -> new CampaignSummary(
                        rs.getLong("id"),
                        rs.getString("label"),
                        rs.getObject("root_posting_id", UUID.class),
                        rs.getInt("member_count"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    public Optional<CampaignDetail> detail(long id) {
        List<CampaignSummary> head = jdbc.query(
                "SELECT id, label, root_posting_id, member_count, created_at FROM campaigns WHERE id = ?",
                (rs, i) -> new CampaignSummary(
                        rs.getLong("id"), rs.getString("label"),
                        rs.getObject("root_posting_id", UUID.class),
                        rs.getInt("member_count"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                id);
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<Member> members = jdbc.query(
                "SELECT p.id, left(p.raw_text, " + SNIPPET + ") AS snippet, p.created_at "
                        + "FROM campaign_members cm JOIN postings p ON p.id = cm.posting_id "
                        + "WHERE cm.campaign_id = ? ORDER BY p.created_at",
                (rs, i) -> new Member(
                        rs.getObject("id", UUID.class),
                        rs.getString("snippet"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                id);
        CampaignSummary s = head.get(0);
        return Optional.of(new CampaignDetail(s.id(), s.label(), s.memberCount(), members));
    }

    public record PostingInfo(UUID id, String snippet, java.time.Instant createdAt) {}

    public record Edge(UUID a, UUID b) {}

    public record ClusterWrite(String label, UUID rootPostingId, List<UUID> memberIds) {}
}
