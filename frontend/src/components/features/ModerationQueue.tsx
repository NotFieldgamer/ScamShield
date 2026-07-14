"use client";

import * as React from "react";
import Link from "next/link";
import { type ReportSummary } from "@/lib/api";
import { pendingReports, reclusterCampaigns, resolveReport, useSession } from "@/lib/auth";
import { PanelSkeleton, TableSkeleton } from "@/components/features/Skeletons";
import { cn } from "@/lib/utils";

export function ModerationQueue() {
  const { me, loading } = useSession();
  const [reports, setReports] = React.useState<ReportSummary[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busyId, setBusyId] = React.useState<number | null>(null);
  const [notice, setNotice] = React.useState<string | null>(null);

  const canModerate = me?.role === "ADMIN";

  const load = React.useCallback(() => {
    setError(null);
    pendingReports()
      .then(setReports)
      .catch((e) => setError(e.message ?? "Could not load the queue."));
  }, []);

  React.useEffect(() => {
    if (canModerate) load();
  }, [canModerate, load]);

  async function decide(id: number, decision: "CONFIRM" | "REJECT") {
    setBusyId(id);
    try {
      await resolveReport(id, decision);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not resolve the report.");
    } finally {
      setBusyId(null);
    }
  }

  async function recluster() {
    setNotice(null);
    try {
      const r = await reclusterCampaigns();
      setNotice(`Reclustered: ${r.campaigns} campaign(s) with two or more near-duplicate postings.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not recluster.");
    }
  }

  if (loading) return <PanelSkeleton lines={2} label="Checking your session…" />;

  if (!canModerate) {
    return (
      <div className="p7-panel">
        <p className="p7-panel-title">Admins only</p>
        <p className="p7-panel-note">
          This queue is restricted to admins. {me ? "Your account isn't an admin." : "You're not signed in."}
        </p>
        {!me && (
          <Link className="ss-btn ss-btn-primary" href="/login">
            Sign in
          </Link>
        )}
      </div>
    );
  }

  return (
    <>
      <div className="p7-actions" style={{ marginBottom: "1rem" }}>
        <button type="button" className="ss-btn ss-btn-ghost" onClick={load}>
          Refresh
        </button>
        <button type="button" className="ss-btn ss-btn-ghost" onClick={recluster}>
          Recluster campaigns
        </button>
      </div>
      {notice && <p className="p7-form-ok" style={{ marginBottom: "1rem" }}>{notice}</p>}
      {error && <p className="p7-form-error" style={{ marginBottom: "1rem" }}>{error}</p>}

      {!reports && <TableSkeleton rows={5} label="Loading the report queue…" />}

      {reports && reports.length === 0 && (
        <div className="p7-empty">
          <p className="p7-empty-title">Queue is clear</p>
          <p className="p7-empty-body">No reports are awaiting a decision.</p>
        </div>
      )}

      {reports && reports.length > 0 && (
        <div className="p7-panel">
          <p className="p7-panel-title">Pending reports</p>
          <p className="p7-panel-note">
            A confirm marks the report <span className="p7-mono">MODERATOR_CONFIRMED</span> — the
            only status retraining reads. Every decision writes an audit row.
          </p>
          <div className="p7-table-wrap">
            <table className="p7-table">
              <thead>
                <tr>
                  <th>Posting</th>
                  <th>Claim</th>
                  <th>Status</th>
                  <th>Filed</th>
                  <th style={{ textAlign: "right" }}>Decision</th>
                </tr>
              </thead>
              <tbody>
                {reports.map((r) => (
                  <tr key={r.id}>
                    <td className="p7-mono" style={{ maxWidth: "16ch", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {r.postingId}
                    </td>
                    <td>
                      <span className={cn("p7-badge", r.claim === "CONFIRMED_SCAM" ? "p7-badge-scam" : "p7-badge-ok")}>
                        {r.claim === "CONFIRMED_SCAM" ? "confirms scam" : "says real"}
                      </span>
                    </td>
                    <td>
                      <span className="p7-badge p7-badge-pending">{r.status}</span>
                    </td>
                    <td className="p7-num" style={{ color: "var(--text-faint)", fontSize: "0.75rem" }}>
                      {new Date(r.createdAt).toLocaleString()}
                    </td>
                    <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                      <button
                        type="button"
                        className="ss-btn ss-btn-ghost"
                        disabled={busyId === r.id}
                        onClick={() => decide(r.id, "REJECT")}
                      >
                        Reject
                      </button>{" "}
                      <button
                        type="button"
                        className="ss-btn ss-btn-primary"
                        disabled={busyId === r.id}
                        onClick={() => decide(r.id, "CONFIRM")}
                      >
                        Confirm
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
