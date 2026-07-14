"use client";

import * as React from "react";
import Link from "next/link";
import { getMyAnalyses } from "@/lib/auth";
import { pct, type AnalysisSummary, type Label } from "@/lib/api";
import { CardGridSkeleton } from "@/components/features/Skeletons";
import { cn } from "@/lib/utils";

const FILTERS: { v: "ALL" | Label; l: string }[] = [
  { v: "ALL", l: "All" },
  { v: "LIKELY_SCAM", l: "Likely scam" },
  { v: "UNCERTAIN", l: "Uncertain" },
  { v: "LIKELY_LEGITIMATE", l: "Likely legit" },
];

const BADGE: Record<Label, string> = {
  LIKELY_SCAM: "p7-badge-scam",
  LIKELY_LEGITIMATE: "p7-badge-ok",
  UNCERTAIN: "p7-badge-pending",
};

const LABEL_TEXT: Record<Label, string> = {
  LIKELY_SCAM: "Likely scam",
  LIKELY_LEGITIMATE: "Likely legit",
  UNCERTAIN: "Uncertain",
};

export function HistoryView() {
  const [data, setData] = React.useState<AnalysisSummary[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [filter, setFilter] = React.useState<"ALL" | Label>("ALL");
  const [query, setQuery] = React.useState("");

  React.useEffect(() => {
    getMyAnalyses()
      .then(setData)
      .catch((e) => setError(e.message ?? "Could not load your history."));
  }, []);

  const filtered = React.useMemo(() => {
    if (!data) return [];
    const q = query.trim().toLowerCase();
    return data.filter(
      (a) =>
        (filter === "ALL" || a.label === filter) &&
        (q === "" || a.snippet.toLowerCase().includes(q)),
    );
  }, [data, filter, query]);

  if (error) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Couldn&apos;t load your history</p>
        <p className="p7-empty-body">{error}</p>
      </div>
    );
  }
  if (!data) return <CardGridSkeleton count={6} label="Loading your history…" />;

  if (data.length === 0) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Nothing analyzed yet</p>
        <p className="p7-empty-body">
          Analyses you run while signed in are saved here.{" "}
          <Link href="/analyze" className="ss-link">
            Analyze a posting
          </Link>{" "}
          and it will show up.
        </p>
      </div>
    );
  }

  return (
    <>
      <div className="hist-controls">
        <div className="p7-actions" role="group" aria-label="Filter by verdict">
          {FILTERS.map((f) => (
            <button
              key={f.v}
              type="button"
              className={cn("ss-btn", filter === f.v ? "ss-btn-primary" : "ss-btn-ghost")}
              aria-pressed={filter === f.v}
              onClick={() => setFilter(f.v)}
            >
              {f.l}
            </button>
          ))}
        </div>
        <input
          type="search"
          className="p7-input hist-search"
          placeholder="Search the text you analyzed"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search history"
        />
      </div>

      <p className="hist-count">
        <span className="p7-num">{filtered.length}</span> of{" "}
        <span className="p7-num">{data.length}</span>{" "}
        {data.length === 1 ? "analysis" : "analyses"}
      </p>

      {filtered.length === 0 ? (
        <div className="p7-empty">
          <p className="p7-empty-title">No matches</p>
          <p className="p7-empty-body">
            No saved analysis matches this filter. Clear the search or choose a different verdict.
          </p>
        </div>
      ) : (
        <div className="hist-grid">
          {filtered.map((a) => (
            <Link key={a.id} href={`/analysis/${a.id}`} className="hist-card surface-card">
              <div className="hist-card__head">
                <span className={cn("p7-badge", BADGE[a.label])}>{LABEL_TEXT[a.label]}</span>
              </div>
              <p className="hist-card__snippet">{a.snippet || "(no text)"}</p>
              <div className="hist-card__foot">
                <span className="p7-num hist-card__prob">P(scam) {pct(a.probability, 1)}</span>
                <span className="hist-card__date">{new Date(a.createdAt).toLocaleDateString()}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
