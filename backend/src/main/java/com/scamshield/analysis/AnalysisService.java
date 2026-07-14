package com.scamshield.analysis;

import ai.onnxruntime.OrtException;
import com.scamshield.analysis.AnalysisRepository.StoredFeature;
import com.scamshield.analysis.AnalysisRepository.StoredVerdict;
import com.scamshield.analysis.dto.AnalysisResponse;
import com.scamshield.analysis.dto.AnalysisResponse.ContributionDto;
import com.scamshield.analysis.dto.AnalysisResponse.PhraseHitDto;
import com.scamshield.analysis.dto.AnalysisResponse.SalaryDto;
import com.scamshield.analysis.dto.AnalysisResponse.SimilarScamDto;
import com.scamshield.analysis.dto.AnalysisResponse.TyposquatDto;
import com.scamshield.audit.AuditRepository;
import com.scamshield.common.NotFoundException;
import com.scamshield.common.RateLimitException;
import com.scamshield.common.TextNormalizer;
import com.scamshield.inference.Explainer;
import com.scamshield.inference.Explainer.Contribution;
import com.scamshield.inference.OnnxClassifier;
import com.scamshield.inference.PlattCalibrator;
import com.scamshield.inference.SalaryPlausibility;
import com.scamshield.inference.SalaryPlausibility.SalaryFlag;
import com.scamshield.inference.TfidfVectorizer;
import com.scamshield.matching.ScamPhrase;
import com.scamshield.matching.ScamPhraseRepository;
import com.scamshield.matching.ScamPhraseScanner;
import com.scamshield.matching.ScamPhraseScanner.PhraseHit;
import com.scamshield.matching.TyposquatDetector;
import com.scamshield.matching.TyposquatDetector.Flag;
import com.scamshield.ratelimit.RedisRateLimiter;
import com.scamshield.similarity.EmbeddingService;
import com.scamshield.similarity.VectorSearchRepository;
import com.scamshield.similarity.VectorSearchRepository.SimilarScam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The nine-step analysis pipeline (brief §H). Runs the steps in order, times each one, persists
 * the result, and writes an audit row. Rate limit → normalize/hash → cache → Aho-Corasick →
 * typosquat → TF-IDF → ONNX → contributions → salary → embed+search → persist → audit.
 */
@Service
public class AnalysisService {

    private static final int TOP_K_CONTRIBUTIONS = 10;
    private static final int PERSIST_FEATURES = 15;
    private static final int SIMILAR_SCAMS = 3;
    private static final int SNIPPET = 200;
    // Display thresholds on the calibrated probability. Placeholders until the notebook's
    // precision>=0.90 operating point (metrics.json) is wired in Phase 7's threshold slider.
    private static final double SCAM_THRESHOLD = 0.60;
    private static final double LEGIT_THRESHOLD = 0.40;

    private final RedisRateLimiter rateLimiter;
    private final AnalysisRepository repo;
    private final ScamPhraseRepository phraseRepo;
    private final TyposquatDetector typosquat;
    private final TfidfVectorizer vectorizer;
    private final OnnxClassifier classifier;
    private final PlattCalibrator calibrator;
    private final Explainer explainer;
    private final SalaryPlausibility salary;
    private final EmbeddingService embedding;
    private final VectorSearchRepository vectorSearch;
    private final AuditRepository audit;

    private volatile ScamPhraseScanner scanner;
    private volatile AnalysisRepository.ModelVersionInfo activeModel;

    public AnalysisService(RedisRateLimiter rateLimiter, AnalysisRepository repo,
                           ScamPhraseRepository phraseRepo, TyposquatDetector typosquat,
                           TfidfVectorizer vectorizer, OnnxClassifier classifier,
                           PlattCalibrator calibrator, Explainer explainer, SalaryPlausibility salary,
                           EmbeddingService embedding, VectorSearchRepository vectorSearch,
                           AuditRepository audit) {
        this.rateLimiter = rateLimiter;
        this.repo = repo;
        this.phraseRepo = phraseRepo;
        this.typosquat = typosquat;
        this.vectorizer = vectorizer;
        this.classifier = classifier;
        this.calibrator = calibrator;
        this.explainer = explainer;
        this.salary = salary;
        this.embedding = embedding;
        this.vectorSearch = vectorSearch;
        this.audit = audit;
    }

    public AnalysisResponse analyze(String text, String kind, String callerKey, String ip) {
        Map<String, Long> timings = new LinkedHashMap<>();
        long start = System.nanoTime();

        long s = System.nanoTime();
        if (!rateLimiter.tryAcquire(callerKey)) {
            throw new RateLimitException("rate limit exceeded for " + callerKey);
        }
        timings.put("rateLimit", millis(s));

        s = System.nanoTime();
        String source = normalizeKind(kind);
        String hash = TextNormalizer.contentHash(text);
        Optional<StoredVerdict> cached = repo.findByContentHash(hash);
        timings.put("normalizeHashCache", millis(s));
        if (cached.isPresent()) {
            return fromStored(cached.get(), text, timings, true);
        }

        try {
            s = System.nanoTime();
            List<PhraseHit> phraseHits = scanner().scan(text);
            timings.put("ahoCorasick", millis(s));

            s = System.nanoTime();
            List<Flag> squats = typosquat.detect(text);
            timings.put("typosquat", millis(s));

            s = System.nanoTime();
            double[] tfidf = vectorizer.vectorize(text);
            timings.put("tfidf", millis(s));

            s = System.nanoTime();
            double raw = classifier.rawProbability(TfidfVectorizer.toFloat(tfidf));
            double calibrated = calibrator.calibrate(raw);
            timings.put("classifier", millis(s));

            s = System.nanoTime();
            List<Contribution> contributions = explainer.topContributions(tfidf, vectorizer, PERSIST_FEATURES);
            timings.put("contributions", millis(s));

            s = System.nanoTime();
            Optional<SalaryFlag> salaryFlag = salary.assess(text);
            timings.put("salary", millis(s));

            s = System.nanoTime();
            float[] emb = embedding.embed(text);
            List<SimilarScam> similar = vectorSearch.nearest(emb, SIMILAR_SCAMS);
            timings.put("embedSearch", millis(s));

            s = System.nanoTime();
            String label = label(calibrated);
            int latencyMs = (int) millis(start);
            UUID verdictId;
            UUID postingId;
            try {
                postingId = repo.insertPosting(null, text, source, hash);
                verdictId = repo.insertVerdict(postingId, activeModel().id(), calibrated, label, latencyMs);
                repo.insertFeatures(verdictId, contributions);
                vectorSearch.savePostingEmbedding(postingId, emb);
                audit.record(null, "ANALYZE", "POSTING", postingId.toString(), ip);
            } catch (DuplicateKeyException race) {
                // Another request persisted the same content_hash first; return its verdict.
                return repo.findByContentHash(hash)
                        .map(v -> fromStored(v, text, timings, true))
                        .orElseThrow(() -> race);
            }
            timings.put("persist", millis(s));

            return new AnalysisResponse(
                    verdictId, label, calibrated, raw,
                    toContributions(contributions.subList(0, Math.min(TOP_K_CONTRIBUTIONS, contributions.size()))),
                    toPhrases(phraseHits), toTyposquats(squats),
                    salaryFlag.map(AnalysisService::toSalary).orElse(null),
                    toSimilar(similar), latencyMs, false, timings,
                    text, activeModel().name(), activeModel().version(), postingId);
        } catch (OrtException e) {
            throw new IllegalStateException("inference failed", e);
        }
    }

    public AnalysisResponse getById(UUID verdictId) {
        StoredVerdict stored = repo.findVerdictById(verdictId)
                .orElseThrow(() -> new NotFoundException("analysis " + verdictId + " not found"));
        return fromStored(stored, stored.rawText(), new LinkedHashMap<>(), true);
    }

    // --- cached / stored reconstruction: cheap re-derivation, no ONNX/embedding recompute ---
    private AnalysisResponse fromStored(StoredVerdict v, String text, Map<String, Long> timings, boolean cached) {
        List<ContributionDto> contributions = repo.findFeatures(v.verdictId()).stream()
                .limit(TOP_K_CONTRIBUTIONS)
                .map(this::toContribution)
                .toList();
        List<PhraseHit> phraseHits = scanner().scan(text);
        List<Flag> squats = typosquat.detect(text);
        Optional<SalaryFlag> salaryFlag = salary.assess(text);
        List<SimilarScam> similar = vectorSearch.nearestForPosting(v.postingId(), SIMILAR_SCAMS);

        return new AnalysisResponse(
                v.verdictId(), v.label(), v.probability(), null,
                contributions, toPhrases(phraseHits), toTyposquats(squats),
                salaryFlag.map(AnalysisService::toSalary).orElse(null),
                toSimilar(similar), v.latencyMs(), cached, timings,
                text, v.modelName(), v.modelVersion(), v.postingId());
    }

    private ScamPhraseScanner scanner() {
        ScamPhraseScanner local = scanner;
        if (local == null) {
            synchronized (this) {
                local = scanner;
                if (local == null) {
                    List<ScamPhrase> phrases = phraseRepo.findAll();
                    local = new ScamPhraseScanner(phrases);
                    scanner = local;
                }
            }
        }
        return local;
    }

    private AnalysisRepository.ModelVersionInfo activeModel() {
        AnalysisRepository.ModelVersionInfo local = activeModel;
        if (local == null) {
            local = repo.activeModel();
            activeModel = local;
        }
        return local;
    }

    private String label(double calibrated) {
        if (calibrated >= SCAM_THRESHOLD) {
            return "LIKELY_SCAM";
        }
        if (calibrated <= LEGIT_THRESHOLD) {
            return "LIKELY_LEGITIMATE";
        }
        return "UNCERTAIN";
    }

    private static String normalizeKind(String kind) {
        return "MESSAGE".equalsIgnoreCase(kind) ? "MESSAGE" : "POSTING";
    }

    private static long millis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private List<ContributionDto> toContributions(List<Contribution> contributions) {
        return contributions.stream()
                .map(c -> new ContributionDto(c.feature(), c.contribution(), c.charNgram()))
                .toList();
    }

    private ContributionDto toContribution(StoredFeature f) {
        return new ContributionDto(f.featureName(), f.contribution(), vectorizer.isCharTerm(f.featureName()));
    }

    private static List<PhraseHitDto> toPhrases(List<PhraseHit> hits) {
        return hits.stream()
                .map(h -> new PhraseHitDto(h.phrase(), h.category(), h.weight(), h.count()))
                .toList();
    }

    private static List<TyposquatDto> toTyposquats(List<Flag> flags) {
        return flags.stream()
                .map(f -> new TyposquatDto(f.candidate(), f.legitimate(), f.distance()))
                .toList();
    }

    private static SalaryDto toSalary(SalaryFlag f) {
        return new SalaryDto(f.amount(), f.period(), f.zScore(), f.implausible());
    }

    private static List<SimilarScamDto> toSimilar(List<SimilarScam> similar) {
        return similar.stream()
                .map(s -> new SimilarScamDto(s.id(), snippet(s.text()), s.source(), s.similarity()))
                .toList();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= SNIPPET ? trimmed : trimmed.substring(0, SNIPPET) + "…";
    }
}
