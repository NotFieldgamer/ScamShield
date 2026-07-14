"use client";

import * as React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { type Claim } from "@/lib/api";
import { submitReport, useSession } from "@/lib/auth";

export function ReportForm() {
  const params = useParams<{ postingId: string }>();
  const postingId = params?.postingId ?? "";
  const { me, loading } = useSession();
  const [status, setStatus] = React.useState<"idle" | "sending" | "done">("idle");
  const [error, setError] = React.useState<string | null>(null);
  const [outcome, setOutcome] = React.useState<string | null>(null);

  async function report(claim: Claim) {
    setStatus("sending");
    setError(null);
    try {
      const summary = await submitReport(postingId, claim);
      setOutcome(
        summary.status === "COMMUNITY_CONFIRMED"
          ? "Report filed. A second independent report agreed — this posting is now community-flagged for moderator review."
          : "Report filed. It will be reviewed by a moderator.",
      );
      setStatus("done");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not file the report.");
      setStatus("idle");
    }
  }

  if (loading) return <div className="p7-panel">Checking your session…</div>;

  if (!me) {
    return (
      <div className="p7-panel">
        <p className="p7-panel-title">Sign in to report</p>
        <p className="p7-panel-note">
          Reporting a verdict needs an account (and one at least 7 days old). This keeps the feedback
          loop from being gamed.
        </p>
        <Link
          className="ss-btn ss-btn-primary"
          href={`/login?next=${encodeURIComponent(`/report/${postingId}`)}`}
        >
          Sign in
        </Link>
      </div>
    );
  }

  if (status === "done") {
    return (
      <div className="p7-panel">
        <p className="p7-form-ok">{outcome}</p>
        <p style={{ marginTop: "1rem" }}>
          <Link className="ss-btn ss-btn-ghost" href="/analyze">
            Analyze another posting
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="p7-panel">
      <p className="p7-panel-title">Dispute this verdict</p>
      <p className="p7-panel-note">
        Posting <span className="p7-mono">{postingId}</span>. Tell us what you believe is true. A
        label only changes on agreement between two independent reporters or a moderator&apos;s
        decision — and only moderator-confirmed reports are ever used to retrain.
      </p>
      {error && <p className="p7-form-error" style={{ marginBottom: "0.75rem" }}>{error}</p>}
      <div className="p7-actions">
        <button
          type="button"
          className="ss-btn ss-btn-ghost"
          disabled={status === "sending"}
          onClick={() => report("FALSE_POSITIVE")}
        >
          This was actually real
        </button>
        <button
          type="button"
          className="ss-btn ss-btn-primary"
          disabled={status === "sending"}
          onClick={() => report("CONFIRMED_SCAM")}
        >
          Confirm scam
        </button>
      </div>
    </div>
  );
}
