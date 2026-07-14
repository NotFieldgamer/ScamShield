"use client";

import * as React from "react";
import Link from "next/link";
import { type AnalysisResponse } from "@/lib/api";
import { GlassCard } from "@/components/glass/GlassCard";
import { GlassPanel } from "@/components/glass/GlassPanel";
import { VerdictBanner } from "./VerdictBanner";
import { HighlightedPosting } from "./HighlightedPosting";
import { FlagList } from "./FlagList";
import { SimilarScams } from "./SimilarScams";

/**
 * The full verdict, in the brief's order: banner → highlighted posting → why it was flagged →
 * similar confirmed scams → footer → feedback. Shared by /analyze and the /analysis/[id]
 * permalink. Every value here comes from the API response.
 */
export function VerdictView({ data }: { data: AnalysisResponse }) {
  const [lit, setLit] = React.useState(false);

  // Fluoresce the flagged phrases shortly after the verdict mounts.
  React.useEffect(() => {
    const t = setTimeout(() => setLit(true), 140);
    return () => clearTimeout(t);
  }, [data.id]);

  const extraSignals = data.salary?.implausible || data.typosquats.length > 0;

  return (
    <div className="az-stack">
      <VerdictBanner data={data} />

      <GlassPanel>
        <h2 className="az-section-title">The posting</h2>
        <HighlightedPosting text={data.text} phrases={data.matchedPhrases} lit={lit} />
      </GlassPanel>

      <GlassCard>
        <h2 className="az-section-title">Why it was flagged</h2>
        <FlagList data={data} />
        {extraSignals && (
          <div style={{ marginTop: "0.9rem", display: "flex", flexDirection: "column", gap: "0.4rem" }}>
            {data.salary?.implausible && (
              <div className="az-flag-row">
                <span className="az-flag-main">
                  <span className="az-flag-term">Salary implausibly high for the role</span>
                  <span className="az-flag-kind">z-score</span>
                </span>
                <span className="az-flag-value az-flag-pos">{data.salary.zScore.toFixed(2)}σ</span>
              </div>
            )}
            {data.typosquats.map((t, i) => (
              <div className="az-flag-row" key={i}>
                <span className="az-flag-main">
                  <span className="az-flag-term">
                    {t.domain} → {t.legitimate}
                  </span>
                  <span className="az-flag-kind">typosquat</span>
                </span>
                <span className="az-flag-value az-flag-pos">edit&nbsp;{t.editDistance}</span>
              </div>
            ))}
          </div>
        )}
      </GlassCard>

      <GlassCard>
        <h2 className="az-section-title">Similar confirmed scams</h2>
        <SimilarScams items={data.similarScams} />
      </GlassCard>

      <div className="az-footer">
        <span>
          model <b>{data.modelName}</b> v{data.modelVersion}
        </span>
        <span>
          <b>{data.latencyMs}</b> ms
        </span>
        {data.cached && <span>· cached</span>}
        <span>
          · verdict <b>{data.id.slice(0, 8)}</b>
        </span>
      </div>

      <div>
        <h2 className="az-section-title" style={{ fontSize: "0.98rem", marginBottom: "0.6rem" }}>
          Was this right?
        </h2>
        <p className="text-faint" style={{ margin: "0 0 0.6rem", fontSize: "0.8rem", lineHeight: 1.5 }}>
          A label only changes on agreement between two independent reporters or a moderator&apos;s
          decision — and only moderator-confirmed reports are ever used to retrain.
        </p>
        <div className="az-feedback">
          <Link className="ss-btn ss-btn-ghost" href={`/report/${data.postingId}`}>
            This was actually real
          </Link>
          <Link className="ss-btn ss-btn-ghost" href={`/report/${data.postingId}`}>
            Confirm scam
          </Link>
        </div>
      </div>
    </div>
  );
}
