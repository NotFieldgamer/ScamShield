"use client";

import * as React from "react";
import { analyze, ApiError, type AnalysisResponse, type AnalyzeKind } from "@/lib/api";
import { GlassCard } from "@/components/glass/GlassCard";
import { GlassPanel } from "@/components/glass/GlassPanel";
import { UvSweep } from "@/components/glass/UvSweep";
import { Button } from "@/components/primitives/Button";
import { ToggleGroup, ToggleGroupItem } from "@/components/primitives/ToggleGroup";
import { VerdictView } from "./VerdictView";

const CANONICAL_SCAM =
  "URGENT hiring! Work from home and earn $5000 per week, no experience needed. We are recruiting " +
  "immediately for a data entry role. To activate your account, send your bank account details and " +
  "a $200 processing fee to start immediately. Guaranteed income. Contact us on WhatsApp today.";

type Status = "idle" | "analyzing" | "done" | "error";

export function Analyzer() {
  const [text, setText] = React.useState("");
  const [kind, setKind] = React.useState<AnalyzeKind>("POSTING");
  const [status, setStatus] = React.useState<Status>("idle");
  const [result, setResult] = React.useState<AnalysisResponse | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  async function onSubmit() {
    if (text.trim().length === 0) return;
    setStatus("analyzing");
    setError(null);
    setResult(null);
    const started = performance.now();
    try {
      const data = await analyze(text, kind);
      // Let the light finish its pass even when the API is instant — still well under two seconds.
      const elapsed = performance.now() - started;
      if (elapsed < 750) await new Promise((r) => setTimeout(r, 750 - elapsed));
      setResult(data);
      setStatus("done");
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.message
          : "Couldn't reach the analyzer. Check the API is running and try again.",
      );
      setStatus("error");
    }
  }

  function reset() {
    setStatus("idle");
    setResult(null);
    setError(null);
    setText("");
  }

  if (status === "done" && result) {
    return (
      <div className="az-stack">
        <VerdictView data={result} />
        <div>
          <Button variant="ghost" size="sm" onClick={reset}>
            Analyze another
          </Button>
        </div>
      </div>
    );
  }

  if (status === "analyzing") {
    // The signature moment: light sweeps the posting while the pipeline runs.
    return (
      <GlassPanel style={{ position: "relative" }}>
        <UvSweep runId={1} />
        <h2 className="az-section-title">Inspecting the posting…</h2>
        <p className="az-posting">{text}</p>
        <div style={{ marginTop: "1.25rem", display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          <div className="az-skel" style={{ height: 14, width: "45%" }} />
          <div className="az-skel" style={{ height: 14, width: "82%" }} />
          <div className="az-skel" style={{ height: 14, width: "68%" }} />
        </div>
      </GlassPanel>
    );
  }

  return (
    <GlassCard>
      <h1 className="az-lede">Is this job real?</h1>
      <p className="az-sub">
        Paste a job posting or a recruiter&apos;s message. In under a second you&apos;ll see whether
        it&apos;s likely a scam — and exactly what gave it away.
      </p>
      <textarea
        className="az-textarea"
        placeholder="Paste the posting or message here…"
        value={text}
        onChange={(e) => setText(e.target.value)}
        aria-label="Posting or message text"
      />
      <div className="az-input-row">
        <ToggleGroup
          type="single"
          value={kind}
          onValueChange={(v) => v && setKind(v as AnalyzeKind)}
          aria-label="What are you pasting?"
        >
          <ToggleGroupItem value="POSTING">Job posting</ToggleGroupItem>
          <ToggleGroupItem value="MESSAGE">Message</ToggleGroupItem>
        </ToggleGroup>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
          {text.trim().length === 0 && (
            <button
              type="button"
              onClick={() => setText(CANONICAL_SCAM)}
              style={{
                background: "none",
                border: 0,
                cursor: "pointer",
                color: "var(--accent)",
                font: "inherit",
                fontSize: "0.8rem",
                textDecoration: "underline",
              }}
            >
              Try an example
            </button>
          )}
          <Button onClick={onSubmit} disabled={text.trim().length === 0}>
            Analyze posting
          </Button>
        </div>
      </div>
      {error && <div className="az-error">{error}</div>}
    </GlassCard>
  );
}
