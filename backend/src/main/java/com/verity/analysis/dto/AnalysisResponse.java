package com.verity.analysis.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The analysis result. Every field traces to a computation in the pipeline:
 * {@code probability} is the calibrated model output; {@code topContributions} are
 * {@code coefficient × tfidf} log-odds terms; {@code similarScams} come from pgvector cosine
 * search. {@code stageMillis} exposes the per-stage latency breakdown (a forensics tool shows it).
 */
public record AnalysisResponse(
        UUID id,
        String label,
        double probability,
        Double rawProbability,
        List<ContributionDto> topContributions,
        List<PhraseHitDto> matchedPhrases,
        List<TyposquatDto> typosquats,
        SalaryDto salary,
        List<SimilarScamDto> similarScams,
        long latencyMs,
        boolean cached,
        Map<String, Long> stageMillis,
        // The posting text (so a permalink can re-render it with phrases highlighted) and the
        // model that produced this verdict (for the footer). Both trace to persisted rows.
        String text,
        String modelName,
        String modelVersion,
        // The posting id (distinct from the verdict id in {@code id}); a report keys on the posting.
        UUID postingId) {

    /** A feature's contribution to the risk score (log-odds), not to the probability. */
    public record ContributionDto(String feature, double contribution, boolean charNgram) {}

    public record PhraseHitDto(String phrase, String category, double weight, int count) {}

    public record TyposquatDto(String domain, String legitimate, int editDistance) {}

    public record SalaryDto(double amount, String period, double zScore, boolean implausible) {}

    public record SimilarScamDto(long id, String textSnippet, String source, double similarity) {}
}
