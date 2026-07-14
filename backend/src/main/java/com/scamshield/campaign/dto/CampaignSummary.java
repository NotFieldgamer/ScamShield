package com.scamshield.campaign.dto;

import java.time.Instant;
import java.util.UUID;

/** One duplicate-campaign cluster: the same scam reposted under many names. */
public record CampaignSummary(
        long id,
        String label,
        UUID rootPostingId,
        int memberCount,
        Instant createdAt) {
}
