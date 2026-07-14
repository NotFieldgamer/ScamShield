"use client";

import * as React from "react";
import Link from "next/link";
import { getCampaigns, type CampaignSummary } from "@/lib/api";

export function CampaignsView() {
  const [data, setData] = React.useState<CampaignSummary[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    getCampaigns()
      .then(setData)
      .catch((e) => setError(e.message ?? "Could not load campaigns."));
  }, []);

  if (error) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Couldn&apos;t load campaigns</p>
        <p className="p7-empty-body">{error}</p>
      </div>
    );
  }
  if (!data) return <div className="p7-panel">Loading campaigns…</div>;

  if (data.length === 0) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">No duplicate campaigns detected yet</p>
        <p className="p7-empty-body">
          When the same scam is reposted under different company names, near-duplicate postings are
          grouped by Union-Find over their embeddings. Clusters of two or more appear here once
          enough postings have been analyzed and an admin triggers reclustering.
        </p>
      </div>
    );
  }

  return (
    <div className="p7-stats" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}>
      {data.map((c) => (
        <Link key={c.id} href={`/campaigns/${c.id}`} className="p7-stat" style={{ textDecoration: "none" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "0.5rem" }}>
            <span className="p7-badge p7-badge-scam">{c.memberCount} reposts</span>
            <span className="p7-threshold-cap">#{c.id}</span>
          </div>
          <p style={{ margin: "0.6rem 0 0", color: "var(--text)", fontSize: "0.9rem", lineHeight: 1.45 }}>
            {c.label}
          </p>
          <span className="p7-stat-sub">View cluster →</span>
        </Link>
      ))}
    </div>
  );
}
