package com.verity.campaign.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A campaign with its member postings — the reposts clustered together. */
public record CampaignDetail(
        long id,
        String label,
        int memberCount,
        List<Member> members) {

    public record Member(UUID postingId, String snippet, Instant createdAt) {}
}
