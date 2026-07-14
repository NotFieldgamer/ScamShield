package com.scamshield.analysis.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in a caller's own analysis history. A lightweight projection of a verdict — enough to list
 * and filter past analyses without shipping the full posting text; the permalink ({@code id}) opens
 * the complete verdict. Every field is read from a stored row, never recomputed.
 */
public record AnalysisSummary(
        UUID id,
        UUID postingId,
        String label,
        double probability,
        String source,
        String snippet,
        Instant createdAt) {
}
