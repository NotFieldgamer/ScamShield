package com.scamshield.campaign;

import com.scamshield.campaign.dto.CampaignDetail;
import com.scamshield.campaign.dto.CampaignSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public campaign browsing plus an admin-triggered rebuild. Reads are public (the transparency
 * page); reclustering mutates shared state, so it is gated to ADMIN.
 */
@RestController
@RequestMapping("/api/v1")
public class CampaignController {

    private final CampaignService campaigns;

    public CampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping("/campaigns")
    public List<CampaignSummary> list() {
        return campaigns.list();
    }

    @GetMapping("/campaigns/{id}")
    public CampaignDetail detail(@PathVariable long id) {
        return campaigns.detail(id);
    }

    @PostMapping("/admin/campaigns/recluster")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> recluster(@AuthenticationPrincipal Long adminId,
                                          HttpServletRequest http) {
        int clusters = campaigns.recluster(adminId, clientIp(http));
        return Map.of("campaigns", clusters);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
