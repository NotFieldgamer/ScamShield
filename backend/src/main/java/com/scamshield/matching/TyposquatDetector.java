package com.scamshield.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flags domains in a posting that are a tiny edit away from a legitimate one — the classic
 * recruiter-scam trick ({@code linkedln.com} for {@code linkedin.com}, {@code 1inkedin.com}, …).
 * Uses {@link Levenshtein} against a registry of known-good domains.
 */
public class TyposquatDetector {

    /** Reasonable default registry of legitimate job-board / big-tech domains. */
    public static final Set<String> DEFAULT_LEGIT_DOMAINS = Set.of(
            "linkedin.com", "indeed.com", "glassdoor.com", "ziprecruiter.com", "monster.com",
            "naukri.com", "wellfound.com", "workday.com", "greenhouse.io", "lever.co",
            "google.com", "gmail.com", "microsoft.com", "outlook.com", "apple.com",
            "amazon.com", "meta.com", "facebook.com");

    // Hostnames: label(.label)+ ending in a 2+ letter TLD. Lower-cased before matching.
    private static final Pattern DOMAIN = Pattern.compile(
            "\\b([a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9-]+)*\\.[a-z]{2,})\\b");

    private final List<String> legitDomains;
    private final Set<String> legitSet;
    private final int maxDistance;

    public TyposquatDetector(Set<String> legitDomains, int maxDistance) {
        this.legitDomains = List.copyOf(legitDomains);
        this.legitSet = Set.copyOf(legitDomains);
        this.maxDistance = maxDistance;
    }

    public TyposquatDetector() {
        this(DEFAULT_LEGIT_DOMAINS, 2);
    }

    public List<Flag> detect(String text) {
        Map<String, Flag> flags = new LinkedHashMap<>(); // dedupe by candidate, keep closest
        Matcher m = DOMAIN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String candidate = registrableTail(m.group(1));
            if (legitSet.contains(candidate)) {
                continue; // exact legitimate domain, not a squat
            }
            String bestLegit = null;
            int best = maxDistance + 1;
            for (String legit : legitDomains) {
                int d = Levenshtein.distanceWithin(candidate, legit, maxDistance);
                if (d > 0 && d < best) {
                    best = d;
                    bestLegit = legit;
                }
            }
            if (bestLegit != null && best <= maxDistance) {
                Flag existing = flags.get(candidate);
                if (existing == null || best < existing.distance()) {
                    flags.put(candidate, new Flag(candidate, bestLegit, best));
                }
            }
        }
        return new ArrayList<>(flags.values());
    }

    /** Reduce a host to its last two labels (example.co -> example.co; a.b.linkedin.com -> linkedin.com). */
    private static String registrableTail(String host) {
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    public record Flag(String candidate, String legitimate, int distance) {}
}
