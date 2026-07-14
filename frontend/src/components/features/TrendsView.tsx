"use client";

import * as React from "react";
import { getTrends, signed, type Trends } from "@/lib/api";
import { cn } from "@/lib/utils";

const RANGES = [
  { v: "7d", l: "7 days" },
  { v: "30d", l: "30 days" },
  { v: "90d", l: "90 days" },
];

export function TrendsView() {
  const [range, setRange] = React.useState("30d");
  const [data, setData] = React.useState<Trends | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    setData(null);
    setError(null);
    getTrends(range)
      .then(setData)
      .catch((e) => setError(e.message ?? "Could not load trends."));
  }, [range]);

  return (
    <>
      <div className="p7-actions" style={{ marginBottom: "1.25rem" }}>
        {RANGES.map((r) => (
          <button
            key={r.v}
            type="button"
            className={cn("ss-btn", range === r.v ? "ss-btn-primary" : "ss-btn-ghost")}
            onClick={() => setRange(r.v)}
          >
            {r.l}
          </button>
        ))}
      </div>

      {error && (
        <div className="p7-empty">
          <p className="p7-empty-title">Couldn&apos;t load trends</p>
          <p className="p7-empty-body">{error}</p>
        </div>
      )}

      {!error && !data && <div className="p7-panel">Loading trends…</div>}

      {data && data.patterns.length === 0 && (
        <div className="p7-empty">
          <p className="p7-empty-title">No patterns recorded in this window yet</p>
          <p className="p7-empty-body">
            Patterns appear here as postings are analyzed. Each row is a word feature that pushed a
            verdict toward &quot;scam&quot;, counted across the last {data.windowDays} days from
            stored verdict features — nothing is estimated.
          </p>
        </div>
      )}

      {data && data.patterns.length > 0 && (
        <div className="p7-panel">
          <p className="p7-panel-title">Rising scam patterns · last {data.windowDays} days</p>
          <p className="p7-panel-note">
            Word features that drove &quot;likely scam&quot; verdicts, with the count this window
            versus the previous {data.windowDays} days. Character n-grams are excluded — they are
            sub-word fragments, not readable phrases.
          </p>
          <div className="p7-table-wrap">
            <table className="p7-table">
              <thead>
                <tr>
                  <th>Pattern</th>
                  <th style={{ textAlign: "right" }}>This window</th>
                  <th style={{ textAlign: "right" }}>Previous</th>
                  <th style={{ textAlign: "right" }}>Change</th>
                  <th style={{ textAlign: "right" }}>Avg log-odds</th>
                </tr>
              </thead>
              <tbody>
                {data.patterns.map((p) => (
                  <tr key={p.feature}>
                    <td className="p7-mono">{p.feature}</td>
                    <td className="p7-num" style={{ textAlign: "right" }}>
                      {p.count.toLocaleString()}
                    </td>
                    <td className="p7-num" style={{ textAlign: "right", color: "var(--text-faint)" }}>
                      {p.previousCount.toLocaleString()}
                    </td>
                    <td className="p7-num" style={{ textAlign: "right" }}>
                      <Delta delta={p.delta} />
                    </td>
                    <td className="p7-num" style={{ textAlign: "right" }}>
                      {signed(p.avgContribution, 3)}
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

function Delta({ delta }: { delta: number }) {
  if (delta > 0) return <span className="p7-delta-up">▲ +{delta.toLocaleString()}</span>;
  if (delta < 0) return <span className="p7-delta-down">▼ {delta.toLocaleString()}</span>;
  return <span className="p7-delta-flat">—</span>;
}
