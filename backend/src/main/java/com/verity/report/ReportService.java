package com.verity.report;

import com.verity.audit.AuditRepository;
import com.verity.auth.User;
import com.verity.auth.UserRepository;
import com.verity.common.ConflictException;
import com.verity.common.ForbiddenException;
import com.verity.common.NotFoundException;
import com.verity.common.UnauthorizedException;
import com.verity.report.dto.ReportSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The feedback loop, treated as an attack surface (brief §H). Three guards live here:
 *
 * <ol>
 *   <li><b>Account age.</b> Only accounts at least seven days old may report, so a scammer cannot
 *       spin up a fresh account to dispute their own verdict.</li>
 *   <li><b>Two-reporter agreement.</b> The community-visible label flips only when two independent
 *       accounts agree — a single report changes nothing.</li>
 *   <li><b>Moderator-only retraining.</b> Community agreement is a hint, never a training signal;
 *       only {@code MODERATOR_CONFIRMED} reports are ever read for retraining
 *       ({@link ReportRepository#moderatorConfirmed()}).</li>
 * </ol>
 *
 * Every state change writes an audit row.
 */
@Service
public class ReportService {

    static final Duration MIN_ACCOUNT_AGE = Duration.ofDays(7);
    private static final int AGREEMENT_THRESHOLD = 2;

    private final ReportRepository reports;
    private final UserRepository users;
    private final AuditRepository audit;

    public ReportService(ReportRepository reports, UserRepository users, AuditRepository audit) {
        this.reports = reports;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public ReportSummary submit(Long userId, UUID postingId, String claim, String ip) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));

        Instant createdAt = user.getCreatedAt();
        if (createdAt == null
                || Duration.between(createdAt, Instant.now()).compareTo(MIN_ACCOUNT_AGE) < 0) {
            throw new ForbiddenException(
                    "Reporting requires an account at least 7 days old. This protects the feedback "
                            + "loop from abuse. Your account is too new to file a report yet.");
        }
        if (!reports.postingExists(postingId)) {
            throw new NotFoundException("posting " + postingId + " not found");
        }

        long id;
        try {
            id = reports.insert(postingId, userId, claim);
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("You have already reported this posting.");
        }
        audit.record(userId, "REPORT_SUBMITTED", "posting", postingId.toString(), ip);

        // Two independent reporters agreeing flips the community label — but NOT the training set.
        if (reports.countActiveReporters(postingId, claim) >= AGREEMENT_THRESHOLD
                && reports.promoteToCommunityConfirmed(postingId, claim) > 0) {
            audit.record(userId, "COMMUNITY_LABEL_CHANGE", "posting", postingId.toString(), ip);
        }

        return reports.findById(id)
                .orElseThrow(() -> new IllegalStateException("report " + id + " vanished after insert"));
    }

    @Transactional
    public void resolve(Long moderatorId, long reportId, boolean confirm, String ip) {
        String status = confirm ? "MODERATOR_CONFIRMED" : "MODERATOR_REJECTED";
        if (reports.resolve(reportId, status, moderatorId) == 0) {
            throw new NotFoundException("report " + reportId + " not found or already resolved");
        }
        audit.record(moderatorId, confirm ? "MODERATION_CONFIRM" : "MODERATION_REJECT",
                "report", String.valueOf(reportId), ip);
    }
}
