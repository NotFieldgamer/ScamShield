package com.scamshield.trends;

import com.scamshield.inference.TfidfVectorizer;
import com.scamshield.trends.TrendsRepository.FeatureCount;
import com.scamshield.trends.dto.TrendsResponse;
import com.scamshield.trends.dto.TrendsResponse.Pattern;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turns raw feature counts into a ranked, human-readable trend list. Character n-grams are dropped
 * because they are sub-word fragments, not phrases a person can read (model card); the page shows
 * word-gram evidence. "Rising" is the delta against the equal-length previous window.
 */
@Service
public class TrendsService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 365;
    private static final int TOP_N = 20;

    private final TrendsRepository repo;
    private final TfidfVectorizer vectorizer;

    public TrendsService(TrendsRepository repo, TfidfVectorizer vectorizer) {
        this.repo = repo;
        this.vectorizer = vectorizer;
    }

    public TrendsResponse trends(String window) {
        int days = parseWindowDays(window);
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(days));
        Instant previousFrom = from.minus(Duration.ofDays(days));

        Map<String, FeatureCount> current = byName(repo.aggregate(from, now));
        Map<String, Long> previous = new HashMap<>();
        for (FeatureCount fc : repo.aggregate(previousFrom, from)) {
            previous.put(fc.featureName(), fc.count());
        }

        List<Pattern> patterns = current.values().stream()
                .filter(fc -> !vectorizer.isCharTerm(fc.featureName())) // word-gram evidence only
                .map(fc -> {
                    long prev = previous.getOrDefault(fc.featureName(), 0L);
                    return new Pattern(fc.featureName(), false, fc.count(), prev,
                            fc.count() - prev, fc.avgContribution());
                })
                // "Rising" means the biggest increase over the previous window, so rank by delta;
                // break ties by absolute volume. (Matches the page's framing and the brief's ask.)
                .sorted(Comparator.comparingLong(Pattern::delta).reversed()
                        .thenComparing(Comparator.comparingLong(Pattern::count).reversed()))
                .limit(TOP_N)
                .toList();

        return new TrendsResponse(days, from, now, patterns);
    }

    private static Map<String, FeatureCount> byName(List<FeatureCount> counts) {
        Map<String, FeatureCount> map = new HashMap<>();
        for (FeatureCount fc : counts) {
            map.put(fc.featureName(), fc);
        }
        return map;
    }

    // Accepts "30d", "7d", or a bare day count. Clamps to [1, 365]; falls back to 30 on garbage.
    private static int parseWindowDays(String window) {
        if (window == null || window.isBlank()) {
            return DEFAULT_WINDOW_DAYS;
        }
        String trimmed = window.trim().toLowerCase();
        if (trimmed.endsWith("d")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            int days = Integer.parseInt(trimmed.trim());
            return Math.max(1, Math.min(MAX_WINDOW_DAYS, days));
        } catch (NumberFormatException e) {
            return DEFAULT_WINDOW_DAYS;
        }
    }
}
