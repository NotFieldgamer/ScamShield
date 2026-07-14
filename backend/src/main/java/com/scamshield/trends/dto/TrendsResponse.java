package com.scamshield.trends.dto;

import java.time.Instant;
import java.util.List;

/**
 * Rising scam patterns over a window, aggregated from {@code verdict_features}. Each pattern
 * carries its count this window, its count in the equal-length previous window, and the delta —
 * all real counts, so "rising" is a measured change, not a decorative badge.
 */
public record TrendsResponse(
        int windowDays,
        Instant from,
        Instant to,
        List<Pattern> patterns) {

    public record Pattern(
            String feature,
            boolean charNgram,
            long count,
            long previousCount,
            long delta,
            double avgContribution) {}
}
