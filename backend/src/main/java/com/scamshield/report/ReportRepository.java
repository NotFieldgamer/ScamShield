package com.scamshield.report;

import com.scamshield.report.dto.ReportSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Community reports: submission, the moderation queue, moderator decisions, and the retraining source. */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT =
            "SELECT id, posting_id, user_id, claim, status, created_at FROM reports ";

    public boolean postingExists(UUID postingId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM postings WHERE id = ?)", Boolean.class, postingId);
        return Boolean.TRUE.equals(exists);
    }

    /** Insert a PENDING report and return its id. A duplicate (same user + posting) throws DuplicateKeyException. */
    public long insert(UUID postingId, long userId, String claim) {
        Long id = jdbc.queryForObject(
                "INSERT INTO reports (posting_id, user_id, claim, status) VALUES (?, ?, ?, 'PENDING') "
                        + "RETURNING id",
                Long.class, postingId, userId, claim);
        return id;
    }

    /**
     * How many distinct accounts have an active (non-rejected) report of this claim on this posting.
     * The unique (user_id, posting_id) constraint guarantees one row per user, so a plain count is a
     * count of distinct reporters. Two of them is the "two independent reporters" agreement threshold.
     */
    public int countActiveReporters(UUID postingId, String claim) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reports WHERE posting_id = ? AND claim = ? "
                        + "AND status <> 'MODERATOR_REJECTED'",
                Integer.class, postingId, claim);
        return count == null ? 0 : count;
    }

    /** Promote agreeing PENDING reports to the community-consensus state. Returns rows changed. */
    public int promoteToCommunityConfirmed(UUID postingId, String claim) {
        return jdbc.update(
                "UPDATE reports SET status = 'COMMUNITY_CONFIRMED' "
                        + "WHERE posting_id = ? AND claim = ? AND status = 'PENDING'",
                postingId, claim);
    }

    /** A moderator decision. Only an unresolved report can be resolved; returns 0 if already resolved. */
    public int resolve(long reportId, String status, long moderatorId) {
        return jdbc.update(
                "UPDATE reports SET status = ?, moderator_id = ?, resolved_at = now() "
                        + "WHERE id = ? AND status IN ('PENDING', 'COMMUNITY_CONFIRMED')",
                status, moderatorId, reportId);
    }

    public Optional<ReportSummary> findById(long id) {
        return jdbc.query(SELECT + "WHERE id = ?", ReportRepository::map, id).stream().findFirst();
    }

    /** Reports still awaiting a moderator decision, newest first. */
    public List<ReportSummary> pending() {
        return jdbc.query(
                SELECT + "WHERE status IN ('PENDING', 'COMMUNITY_CONFIRMED') "
                        + "ORDER BY created_at DESC, id DESC LIMIT 200",
                ReportRepository::map);
    }

    /**
     * The <em>only</em> reports retraining is allowed to consume: moderator-confirmed ones. Community
     * agreement and pending reports are deliberately excluded, so a scammer self-reporting — even with
     * two accounts — can never reach the training set. This is the query that enforces brief §H.
     */
    public List<ReportSummary> moderatorConfirmed() {
        return jdbc.query(
                SELECT + "WHERE status = 'MODERATOR_CONFIRMED' ORDER BY resolved_at DESC, id DESC",
                ReportRepository::map);
    }

    private static ReportSummary map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ReportSummary(
                rs.getLong("id"),
                rs.getObject("posting_id", UUID.class),
                rs.getLong("user_id"),
                rs.getString("claim"),
                rs.getString("status"),
                // pgjdbc maps timestamptz to OffsetDateTime, not directly to Instant.
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
