"use client";

import * as React from "react";
import { getCampaign, getCampaigns, type CampaignDetail, type CampaignSummary } from "@/lib/api";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/primitives/Dialog";

export function CampaignsView() {
  const [data, setData] = React.useState<CampaignSummary[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [openId, setOpenId] = React.useState<number | null>(null);

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
    <>
      <div
        className="p7-stats"
        style={{ gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}
      >
        {data.map((c) => (
          <button
            key={c.id}
            type="button"
            className="p7-stat p7-cluster-card"
            onClick={() => setOpenId(c.id)}
            aria-haspopup="dialog"
          >
            <div className="p7-cluster-card__head">
              <span className="p7-badge p7-badge-scam">{c.memberCount} reposts</span>
              <span className="p7-threshold-cap">#{c.id}</span>
            </div>
            <p className="p7-cluster-card__label">{c.label}</p>
            <span className="p7-stat-sub">Open cluster →</span>
          </button>
        ))}
      </div>

      <Dialog
        open={openId !== null}
        onOpenChange={(open) => {
          if (!open) setOpenId(null);
        }}
      >
        <DialogContent surface="flat" className="ss-dialog--wide">
          {openId !== null && <ClusterDetail id={openId} />}
        </DialogContent>
      </Dialog>
    </>
  );
}

/** The members of one cluster, fetched on open. Always renders a DialogTitle so the dialog is labelled. */
function ClusterDetail({ id }: { id: number }) {
  const [detail, setDetail] = React.useState<CampaignDetail | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setDetail(null);
    setError(null);
    getCampaign(id)
      .then(setDetail)
      .catch((e) => setError(e.message ?? "Could not load this cluster."));
  }, [id]);

  return (
    <div className="p7-cluster-dialog">
      <div className="p7-cluster-dialog__head">
        <DialogTitle className="p7-cluster-dialog__title">
          {detail ? (
            <>
              <span className="p7-badge p7-badge-scam">{detail.memberCount} reposts</span>{" "}
              Campaign #{detail.id}
            </>
          ) : (
            <>Campaign #{id}</>
          )}
        </DialogTitle>
        <DialogClose className="p7-dialog-close" aria-label="Close">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <line x1="6" y1="6" x2="18" y2="18" />
            <line x1="18" y1="6" x2="6" y2="18" />
          </svg>
        </DialogClose>
      </div>

      <DialogDescription className="p7-cluster-dialog__desc">
        These postings were clustered as near-duplicates by cosine similarity of their embeddings —
        one scam, reposted under different names.
      </DialogDescription>

      {error && <p className="p7-form-error">{error}</p>}
      {!error && !detail && <p className="p7-panel-note">Loading cluster…</p>}

      {detail && (
        <div className="p7-cluster-dialog__scroll">
          {detail.members.map((m, i) => (
            <div className="p7-cluster-member" key={m.postingId}>
              <div className="p7-cluster-member__meta">
                <span className="p7-threshold-cap">repost {i + 1}</span>
                <span className="p7-num p7-cluster-member__time">
                  {new Date(m.createdAt).toLocaleString()}
                </span>
              </div>
              <p className="p7-cluster-member__snippet">{m.snippet}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
