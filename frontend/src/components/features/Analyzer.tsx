"use client";

import * as React from "react";
import { analyze, ApiError, type AnalysisResponse, type AnalyzeKind } from "@/lib/api";
import { UvSweep } from "@/components/glass/UvSweep";
import { Button } from "@/components/primitives/Button";
import { ToggleGroup, ToggleGroupItem } from "@/components/primitives/ToggleGroup";
import { VerdictBanner } from "./VerdictBanner";
import { HighlightedPosting } from "./HighlightedPosting";
import { FlagList } from "./FlagList";
import { SimilarScams } from "./SimilarScams";
import { AmbientPanel } from "./AmbientPanel";
import { Feedback } from "./Feedback";
import { ANALYZE_HANDOFF_KEY, CANONICAL_SCAM } from "@/lib/handoff";

type Status = "idle" | "analyzing" | "done" | "error";

export function Analyzer() {
  const [text, setText] = React.useState("");
  const [kind, setKind] = React.useState<AnalyzeKind>("POSTING");
  const [status, setStatus] = React.useState<Status>("idle");
  const [result, setResult] = React.useState<AnalysisResponse | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [runId, setRunId] = React.useState(0);
  const [lit, setLit] = React.useState(false);
  const panelRef = React.useRef<HTMLElement>(null);
  const textareaRef = React.useRef<HTMLTextAreaElement>(null);
  const pendingAuto = React.useRef(false);

  async function onSubmit() {
    if (text.trim().length === 0) return;
    setStatus("analyzing");
    setError(null);
    setResult(null);
    setLit(false);
    setRunId((n) => n + 1);
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

  // Once the verdict resolves: fluoresce the flagged phrases, and on a phone (where the panel sits
  // below the paste area) bring it into view. The paste column never moves, so nothing jumps.
  React.useEffect(() => {
    if (status !== "done" || !result) return;
    const t = setTimeout(() => setLit(true), 160);
    if (typeof window !== "undefined" && window.innerWidth < 900) {
      const smooth = !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      panelRef.current?.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
    }
    return () => clearTimeout(t);
  }, [status, result]);

  // A posting handed off from the landing hero: prefill it and run it once.
  React.useEffect(() => {
    let handoff: string | null = null;
    try {
      handoff = sessionStorage.getItem(ANALYZE_HANDOFF_KEY);
      if (handoff) sessionStorage.removeItem(ANALYZE_HANDOFF_KEY);
    } catch {
      /* sessionStorage unavailable */
    }
    if (handoff && handoff.trim()) {
      setText(handoff);
      pendingAuto.current = true;
    }
  }, []);

  React.useEffect(() => {
    if (pendingAuto.current && text.trim() && status === "idle") {
      pendingAuto.current = false;
      void onSubmit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, status]);

  function reset() {
    setStatus("idle");
    setResult(null);
    setError(null);
    setLit(false);
    setText("");
    textareaRef.current?.focus();
  }

  const extraSignals =
    result != null && (result.salary?.implausible || result.typosquats.length > 0);

  return (
    <div className="az-wrap">
      <header className="az-head">
        <h1 className="az-lede">Is this job real?</h1>
        <p className="az-sub">
          Paste a job posting or a recruiter&apos;s message. In under a second you&apos;ll see whether
          it&apos;s likely a scam — and exactly what gave it away.
        </p>
      </header>

      <div className="az-grid">
        {/* LEFT — the paste area, the primary action. It stays put across every state. */}
        <section className="az-left surface-card">
          <h2 className="az-input-title">Paste the posting</h2>
          <p className="az-input-help">
            A job post or a recruiter&apos;s message. It&apos;s analysed on submit — nothing is kept
            against your name unless you&apos;re signed in.
          </p>
          <textarea
            ref={textareaRef}
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
            <div className="az-input-actions">
              {text.trim().length === 0 && (
                <button
                  type="button"
                  className="az-example-link"
                  onClick={() => setText(CANONICAL_SCAM)}
                >
                  Try an example
                </button>
              )}
              <Button
                onClick={onSubmit}
                disabled={text.trim().length === 0 || status === "analyzing"}
              >
                {status === "analyzing" ? "Analyzing…" : "Analyze posting"}
              </Button>
            </div>
          </div>
        </section>

        {/* RIGHT — the living panel. Ambient content resolves INTO the verdict, in place. */}
        <section className="az-right" ref={panelRef} aria-live="polite">
          <div className="az-panel surface-card">
            {status === "analyzing" && <UvSweep runId={runId} />}

            {status === "idle" && <AmbientPanel />}

            {status === "analyzing" && (
              <div className="az-panel-inner">
                <p className="az-eyebrow">Inspecting the posting…</p>
                <HighlightedPosting text={text} phrases={[]} lit={false} />
                <div className="az-skel-stack">
                  <div className="az-skel" style={{ height: 14, width: "45%" }} />
                  <div className="az-skel" style={{ height: 14, width: "82%" }} />
                  <div className="az-skel" style={{ height: 14, width: "68%" }} />
                </div>
              </div>
            )}

            {status === "error" && (
              <div className="az-panel-inner az-panel-center">
                <p className="az-error">{error}</p>
                <Button variant="ghost" size="sm" onClick={() => setStatus("idle")}>
                  Back
                </Button>
              </div>
            )}

            {status === "done" && result && (
              <div className="az-panel-inner az-verdict">
                <VerdictBanner data={result} />

                <div className="az-verdict-section">
                  <p className="az-eyebrow">The posting</p>
                  <div className="az-posting-well">
                    <HighlightedPosting text={result.text} phrases={result.matchedPhrases} lit={lit} />
                  </div>
                </div>

                <div className="az-verdict-section">
                  <p className="az-eyebrow">Why it was flagged</p>
                  <FlagList data={result} />
                  {extraSignals && (
                    <div className="az-extra-signals">
                      {result.salary?.implausible && (
                        <div className="az-flag-row">
                          <span className="az-flag-main">
                            <span className="az-flag-term">Salary implausibly high for the role</span>
                            <span className="az-flag-kind">z-score</span>
                          </span>
                          <span className="az-flag-value az-flag-pos">
                            {result.salary.zScore.toFixed(2)}σ
                          </span>
                        </div>
                      )}
                      {result.typosquats.map((t, i) => (
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
                </div>

                <div className="az-verdict-section">
                  <p className="az-eyebrow">Similar confirmed scams</p>
                  <SimilarScams items={result.similarScams} />
                </div>

                <div className="az-footer">
                  <span>
                    model <b>{result.modelName}</b> v{result.modelVersion}
                  </span>
                  <span>
                    <b>{result.latencyMs}</b> ms
                  </span>
                  {result.cached && <span>· cached</span>}
                  <span>
                    · verdict <b>{result.id.slice(0, 8)}</b>
                  </span>
                </div>

                <Feedback postingId={result.postingId} />

                <div>
                  <Button variant="ghost" size="sm" onClick={reset}>
                    Analyze another
                  </Button>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
