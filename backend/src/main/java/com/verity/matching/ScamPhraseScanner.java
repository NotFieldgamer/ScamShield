package com.verity.matching;

import com.verity.matching.AhoCorasickMatcher.Match;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Case-insensitive scan of a posting against the scam-phrase registry, in one Aho-Corasick pass.
 * Built once from the {@code scam_phrases} table and reused for every request.
 */
public class ScamPhraseScanner {

    private final List<ScamPhrase> phrases;
    private final AhoCorasickMatcher matcher;

    public ScamPhraseScanner(List<ScamPhrase> phrases) {
        this.phrases = List.copyOf(phrases);
        List<String> patterns = this.phrases.stream()
                .map(p -> p.phrase().toLowerCase(Locale.ROOT))
                .toList();
        this.matcher = new AhoCorasickMatcher(patterns);
    }

    public List<PhraseHit> scan(String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Match match : matcher.findAll(haystack)) {
            counts.merge(match.patternIndex(), 1, Integer::sum);
        }
        List<PhraseHit> hits = new ArrayList<>(counts.size());
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            ScamPhrase phrase = phrases.get(e.getKey());
            hits.add(new PhraseHit(phrase.phrase(), phrase.category(), phrase.weight(), e.getValue()));
        }
        return hits;
    }

    public int phraseCount() {
        return phrases.size();
    }

    public record PhraseHit(String phrase, String category, double weight, int count) {}
}
