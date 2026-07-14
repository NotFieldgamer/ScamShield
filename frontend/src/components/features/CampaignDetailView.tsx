"use client";

import * as React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { getCampaign, type CampaignDetail } from "@/lib/api";

export function CampaignDetailView() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const [data, setData] = React.useState<CampaignDetail | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!id) return;
    getCampaign(id)
      .then(setData)
      .catch((e) => setError(e.message ?? "Could not load this campaign."));
  }, [id]);

  if (error) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Campaign not found</p>
        <p className="p7-empty-body">{error}</p>
        <p style={{ marginTop: "1rem" }}>
          <Link className="ss-btn ss-btn-ghost" href="/campaigns">
            Back to campaigns
          </Link>
        </p>
      </div>
    );
  }
  if (!data) return <div className="p7-panel">Loading campaign…</div>;

  return (
    <>
      <div className="p7-panel">
        <p className="p7-panel-title">
          <span className="p7-badge p7-badge-scam">{data.memberCount} reposts</span>{" "}
          &nbsp;Campaign #{data.id}
        </p>
        <p className="p7-panel-note">
          These postings were clustered as near-duplicates by cosine similarity of their embeddings
          — one scam, reposted under different names.
        </p>
      </div>

      {data.members.map((m, i) => (
        <div className="p7-panel" key={m.postingId}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
            <span className="p7-threshold-cap">repost {i + 1}</span>
            <span className="p7-num" style={{ color: "var(--text-faint)", fontSize: "0.72rem" }}>
              {new Date(m.createdAt).toLocaleString()}
            </span>
          </div>
          <p className="az-posting" style={{ marginTop: "0.5rem", fontSize: "0.9rem" }}>
            {m.snippet}
          </p>
        </div>
      ))}

      <p style={{ marginTop: "1.5rem" }}>
        <Link className="ss-btn ss-btn-ghost" href="/campaigns">
          ← All campaigns
        </Link>
      </p>
    </>
  );
}
