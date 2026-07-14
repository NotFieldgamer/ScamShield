"use client";

import * as React from "react";
import Link from "next/link";
import { analyzeBulk } from "@/lib/auth";
import { pct, type BulkResult, type Label } from "@/lib/api";
import { cn } from "@/lib/utils";

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

export function BulkScanView() {
  const [file, setFile] = React.useState<File | null>(null);
  const [result, setResult] = React.useState<BulkResult | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const inputRef = React.useRef<HTMLInputElement>(null);

  async function onScan() {
    if (!file || busy) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await analyzeBulk(file));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't scan that file. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="p7-panel bulk-upload">
        <p className="p7-panel-title">Upload a CSV</p>
        <p className="bulk-hint">
          Put one posting per row, with the posting text in the first column. A header row named
          &quot;text&quot; is skipped. Up to 100 rows per file, about 1 MB. Each row is scored by the
          same pipeline as a single paste and saved to your history.
        </p>

        <input
          ref={inputRef}
          id="bulk-file"
          className="bulk-file-input"
          type="file"
          accept=".csv,text/csv"
          onChange={(e) => {
            setFile(e.target.files?.[0] ?? null);
            setError(null);
          }}
        />
        <div className="bulk-file-row">
          <button
            type="button"
            className="ss-btn ss-btn-ghost"
            onClick={() => inputRef.current?.click()}
          >
            Choose CSV
          </button>
          <span className="bulk-filename">{file ? file.name : "No file chosen"}</span>
        </div>

        <div className="p7-actions">
          <button type="button" className="ss-btn ss-btn-primary" disabled={!file || busy} onClick={onScan}>
            {busy ? "Scanning…" : "Scan file"}
          </button>
        </div>

        {error && <p className="p7-form-error">{error}</p>}
      </div>

      {result && (
        <>
          <div className="p7-stats bulk-summary">
            <div className="p7-stat">
              <span className="p7-stat-num tone-accent">{result.total}</span>
              <span className="p7-stat-label">postings scanned</span>
            </div>
            <div className="p7-stat">
              <span className="p7-stat-num tone-danger">{result.scam}</span>
              <span className="p7-stat-label">likely scam</span>
            </div>
            <div className="p7-stat">
              <span className="p7-stat-num tone-caution">{result.uncertain}</span>
              <span className="p7-stat-label">uncertain</span>
            </div>
            <div className="p7-stat">
              <span className="p7-stat-num tone-verified">{result.legit}</span>
              <span className="p7-stat-label">likely legit</span>
            </div>
          </div>

          <div className="p7-panel">
            <p className="p7-panel-title">Results</p>
            <p className="p7-panel-note">
              Every row scored by the served model. P(scam) is the calibrated probability; open a row
              to see the full analysis with the signals behind it.
            </p>
            <div className="p7-table-wrap">
              <table className="p7-table">
                <thead>
                  <tr>
                    <th style={{ textAlign: "right" }}>Row</th>
                    <th>Verdict</th>
                    <th style={{ textAlign: "right" }}>P(scam)</th>
                    <th>Posting</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((r) => (
                    <tr key={r.id}>
                      <td className="p7-num" style={{ textAlign: "right" }}>
                        {r.line}
                      </td>
                      <td>
                        <span className={cn("p7-badge", BADGE[r.label])}>{LABEL_TEXT[r.label]}</span>
                      </td>
                      <td className="p7-num" style={{ textAlign: "right" }}>
                        {pct(r.probability, 1)}
                      </td>
                      <td>{r.snippet}</td>
                      <td>
                        <Link href={`/analysis/${r.id}`} className="ss-link">
                          View →
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  );
}
