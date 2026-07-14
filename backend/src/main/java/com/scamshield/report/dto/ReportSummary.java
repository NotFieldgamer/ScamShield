package com.scamshield.report.dto;

import java.time.Instant;
import java.util.UUID;

/** A community report awaiting moderation. */
public record ReportSummary(
        Long id,
        UUID postingId,
        Long userId,
        String claim,
        String status,
        Instant createdAt) {
}
