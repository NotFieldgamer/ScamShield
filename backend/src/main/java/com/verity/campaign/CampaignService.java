package com.verity.campaign;

import com.verity.audit.AuditRepository;
import com.verity.campaign.CampaignRepository.ClusterWrite;
import com.verity.campaign.CampaignRepository.Edge;
import com.verity.campaign.CampaignRepository.PostingInfo;
import com.verity.campaign.dto.CampaignDetail;
import com.verity.campaign.dto.CampaignSummary;
import com.verity.common.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clusters near-duplicate postings into campaigns with {@link UnionFind}. Two postings are joined
 * when their embeddings are near-identical (cosine similarity ≥ the configured threshold); each
 * resulting component of two or more postings is one scam reposted under different names.
 */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private static final int MAX_EDGES = 200_000;
    // Cluster only the most recent postings so the self-join stays bounded (O(MAX_POSTINGS²)).
    private static final int MAX_POSTINGS = 5_000;
    private static final int LABEL_MAX = 120;

    private final CampaignRepository repo;
    private final AuditRepository audit;
    private final double similarityThreshold;

    public CampaignService(CampaignRepository repo, AuditRepository audit,
                           @Value("${app.campaigns.similarity-threshold:0.90}") double similarityThreshold) {
        this.repo = repo;
        this.audit = audit;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * Rebuild all campaigns from recent postings. Returns the number of clusters written. Runs in a
     * single transaction so the table rewrite and the audit row commit atomically — a state change
     * is never persisted without its audit trail.
     */
    @Transactional
    public int recluster(Long actorId, String ip) {
        int totalEmbedded = repo.embeddingCount();
        if (totalEmbedded > MAX_POSTINGS) {
            // Never silently drop data: say what was excluded.
            log.warn("Campaign clustering limited to the most recent {} of {} embedded postings",
                    MAX_POSTINGS, totalEmbedded);
        }
        List<PostingInfo> postings = repo.postings(MAX_POSTINGS);
        int n = postings.size();

        // Map each posting id to a dense index for the Union-Find, and keep the reverse lookup.
        Map<UUID, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(postings.get(i).id(), i);
        }

        UnionFind uf = new UnionFind(n);
        double maxDistance = 1.0 - similarityThreshold; // pgvector <=> is cosine distance
        for (Edge e : repo.nearDuplicateEdges(maxDistance, MAX_POSTINGS, MAX_EDGES)) {
            Integer a = index.get(e.a());
            Integer b = index.get(e.b());
            if (a != null && b != null) {
                uf.union(a, b);
            }
        }

        // Gather components: root index → member postings.
        Map<Integer, List<PostingInfo>> components = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            components.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(postings.get(i));
        }

        List<ClusterWrite> clusters = new ArrayList<>();
        for (List<PostingInfo> members : components.values()) {
            if (members.size() < 2) {
                continue; // a singleton is not a campaign
            }
            // The earliest posting is the original; the rest are the reposts.
            PostingInfo root = members.stream()
                    .min(Comparator.comparing(PostingInfo::createdAt))
                    .orElse(members.get(0));
            clusters.add(new ClusterWrite(label(root.snippet()), root.id(),
                    members.stream().map(PostingInfo::id).toList()));
        }

        int written = repo.rebuild(clusters);
        audit.record(actorId, "CAMPAIGN_RECLUSTER", "campaign", String.valueOf(written), ip);
        return written;
    }

    public List<CampaignSummary> list() {
        return repo.list();
    }

    public CampaignDetail detail(long id) {
        return repo.detail(id).orElseThrow(() -> new NotFoundException("campaign " + id + " not found"));
    }

    private static String label(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "Untitled campaign";
        }
        String oneLine = snippet.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= LABEL_MAX ? oneLine : oneLine.substring(0, LABEL_MAX) + "…";
    }
}
