"use client";

import * as React from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Slider } from "@/components/primitives/Slider";
import { PanelSkeleton } from "@/components/features/Skeletons";
import { getModelMetrics, pct, type ModelMetrics, type ThresholdPoint } from "@/lib/api";

// Fixed brand colors — vivid enough to read in both themes (the signal ramp is verdict-only,
// the UV accent is interaction-only, both honored here).
const UV = "#7c6bff";
const DANGER = "#f2545b";
const CAUTION = "#f2a03d";
const GRIDLINE = "rgba(128,132,148,0.25)";
const AXIS = "#8b90a0";

export function ModelExplorer() {
  const [metrics, setMetrics] = React.useState<ModelMetrics | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [idx, setIdx] = React.useState(50); // grid index 0..100 == threshold 0.00..1.00

  React.useEffect(() => {
    getModelMetrics()
      .then((m) => {
        setMetrics(m);
        if (m.operating) {
          setIdx(Math.round(m.operating.threshold * 100));
        }
      })
      .catch((e) => setError(e.message ?? "Could not load model metrics."));
  }, []);

  if (error) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Couldn&apos;t reach the model service</p>
        <p className="p7-empty-body">{error}</p>
      </div>
    );
  }
  if (!metrics) {
    return <PanelSkeleton lines={4} label="Loading model metrics…" />;
  }
  if (!metrics.hasPredictions) {
    return (
      <div className="p7-empty">
        <p className="p7-empty-title">Validation predictions not loaded</p>
        <p className="p7-empty-body">
          The threshold slider recomputes every number from the served model&apos;s held-out
          predictions. None are seeded for{" "}
          <code>
            {metrics.modelName} {metrics.modelVersion}
          </code>{" "}
          yet. Run <code>ml/notebooks/02_train_classifier.ipynb</code> and load{" "}
          <code>validation_predictions_seed.sql</code> after the V4 migration. Until then this page
          shows nothing rather than an invented curve.
        </p>
      </div>
    );
  }

  const point: ThresholdPoint = metrics.grid[Math.min(idx, metrics.grid.length - 1)];

  return (
    <>
      {/* ---- the threshold slider: the four numbers below move with it ---- */}
      <div className="p7-panel">
        <p className="p7-panel-title">Operating threshold</p>
        <p className="p7-panel-note">
          Flag a posting when the model&apos;s calibrated fraud probability is at or above this
          threshold. Raising it blocks fewer real jobs but lets more scams through; lowering it does
          the reverse. Every figure recomputes live from {metrics.total.toLocaleString()} stored
          held-out predictions.
        </p>
        <div className="p7-threshold">
          <Slider
            value={[idx]}
            min={0}
            max={100}
            step={1}
            onValueChange={(v) => setIdx(v[0])}
            aria-label="Decision threshold"
          />
          <div style={{ textAlign: "right" }}>
            <span className="p7-threshold-readout">{point.threshold.toFixed(2)}</span>
            <span className="p7-threshold-cap">threshold</span>
          </div>
        </div>

        <div className="p7-stats" style={{ marginTop: "0.5rem" }}>
          <div className="p7-stat">
            <div className="p7-stat-num tone-accent">{pct(point.precision, 1)}</div>
            <span className="p7-stat-label">precision — of flagged posts, how many are real scams</span>
          </div>
          <div className="p7-stat">
            <div className="p7-stat-num tone-accent">{pct(point.recall, 1)}</div>
            <span className="p7-stat-label">recall — of all scams, how many we catch</span>
          </div>
          <div className="p7-stat">
            <div className="p7-stat-num tone-caution">{point.fp.toLocaleString()}</div>
            <span className="p7-stat-label">real jobs blocked (false positives)</span>
          </div>
          <div className="p7-stat">
            <div className="p7-stat-num tone-danger">{point.fn.toLocaleString()}</div>
            <span className="p7-stat-label">scams let through (false negatives)</span>
          </div>
        </div>
      </div>

      {/* ---- confusion matrix at the chosen threshold ---- */}
      <div className="p7-panel">
        <p className="p7-panel-title">Confusion matrix at threshold {point.threshold.toFixed(2)}</p>
        <p className="p7-panel-note">
          Rows are the truth, columns are the model&apos;s call — all four counts from the stored
          predictions.
        </p>
        <div className="p7-confusion">
          <div className="p7-cm-cell p7-cm-tp">
            <span className="p7-cm-kind">actual scam · flagged</span>
            <span className="p7-cm-num tone-verified">{point.tp.toLocaleString()}</span>
            <span className="p7-cm-label">scams caught (true positives)</span>
          </div>
          <div className="p7-cm-cell p7-cm-fn">
            <span className="p7-cm-kind">actual scam · cleared</span>
            <span className="p7-cm-num tone-danger">{point.fn.toLocaleString()}</span>
            <span className="p7-cm-label">scams let through (false negatives)</span>
          </div>
          <div className="p7-cm-cell p7-cm-fp">
            <span className="p7-cm-kind">actual real · flagged</span>
            <span className="p7-cm-num tone-caution">{point.fp.toLocaleString()}</span>
            <span className="p7-cm-label">real jobs blocked (false positives)</span>
          </div>
          <div className="p7-cm-cell p7-cm-tn">
            <span className="p7-cm-kind">actual real · cleared</span>
            <span className="p7-cm-num tone-verified">{point.tn.toLocaleString()}</span>
            <span className="p7-cm-label">real jobs cleared (true negatives)</span>
          </div>
        </div>
      </div>

      {/* ---- headline scores ---- */}
      <div className="p7-panel">
        <p className="p7-panel-title">How the served model scores</p>
        <p className="p7-panel-note">
          Reported on {metrics.positives.toLocaleString()} frauds and{" "}
          {metrics.negatives.toLocaleString()} legitimate posts in the untouched held-out split.
          Accuracy is deliberately absent — on a {pct(metrics.noSkillFloor ?? 0, 1)} fraud base rate
          it is a misleading headline.
        </p>
        <div className="p7-stats">
          <Stat value={fixed(metrics.prAuc, 3)} label="PR-AUC (primary)" />
          <Stat value={pct(metrics.noSkillFloor ?? 0, 2)} label="no-skill PR floor (prevalence)" sub="a blind guesser scores this" />
          <Stat value={fixed(metrics.rocAuc, 3)} label="ROC-AUC" />
          <Stat value={fixed(metrics.brier, 4)} label="Brier score (lower is better)" />
          {metrics.operating && (
            <Stat
              value={pct(metrics.operating.recall, 1)}
              label="recall at precision ≥ 90%"
              sub={`threshold ${metrics.operating.threshold.toFixed(2)}`}
              tone="tone-accent"
            />
          )}
        </div>
      </div>

      {/* ---- curves ---- */}
      <div className="p7-charts">
        <div className="p7-panel">
          <p className="p7-chart-title">Precision–recall curve</p>
          <p className="p7-chart-note">
            The real tradeoff. The dashed line is the no-skill floor; the dot is where the slider
            sits now.
          </p>
          <div className="p7-chart">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart margin={{ top: 8, right: 12, bottom: 24, left: 4 }}>
                <CartesianGrid stroke={GRIDLINE} />
                <XAxis
                  type="number"
                  dataKey="recall"
                  domain={[0, 1]}
                  stroke={AXIS}
                  tick={{ fontSize: 11, fill: AXIS }}
                  label={{ value: "recall", position: "insideBottom", offset: -12, fill: AXIS, fontSize: 11 }}
                />
                <YAxis
                  type="number"
                  domain={[0, 1]}
                  stroke={AXIS}
                  tick={{ fontSize: 11, fill: AXIS }}
                  label={{ value: "precision", angle: -90, position: "insideLeft", fill: AXIS, fontSize: 11 }}
                />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(v: number) => v.toFixed(3)}
                  labelFormatter={() => ""}
                />
                <ReferenceLine
                  y={metrics.noSkillFloor ?? 0}
                  stroke={AXIS}
                  strokeDasharray="4 4"
                />
                <Line
                  data={metrics.pr}
                  dataKey="precision"
                  stroke={UV}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
                <ReferenceDot x={point.recall} y={point.precision} r={5} fill={UV} stroke="#fff" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="p7-panel">
          <p className="p7-chart-title">Calibration</p>
          <p className="p7-chart-note">
            When it says 80%, is it right 80% of the time? Points on the diagonal are perfectly
            calibrated.
          </p>
          <div className="p7-chart">
            <ResponsiveContainer width="100%" height="100%">
              <ScatterChart margin={{ top: 8, right: 12, bottom: 24, left: 4 }}>
                <CartesianGrid stroke={GRIDLINE} />
                <XAxis
                  type="number"
                  dataKey="meanPredicted"
                  domain={[0, 1]}
                  stroke={AXIS}
                  tick={{ fontSize: 11, fill: AXIS }}
                  label={{ value: "predicted probability", position: "insideBottom", offset: -12, fill: AXIS, fontSize: 11 }}
                />
                <YAxis
                  type="number"
                  dataKey="empirical"
                  domain={[0, 1]}
                  stroke={AXIS}
                  tick={{ fontSize: 11, fill: AXIS }}
                  label={{ value: "observed fraud fraction", angle: -90, position: "insideLeft", fill: AXIS, fontSize: 11 }}
                />
                <Tooltip contentStyle={tooltipStyle} formatter={(v: number) => v.toFixed(3)} />
                <ReferenceLine segment={[{ x: 0, y: 0 }, { x: 1, y: 1 }]} stroke={AXIS} strokeDasharray="4 4" />
                <Scatter data={metrics.calibration} fill={CAUTION} line={{ stroke: CAUTION }} isAnimationActive={false} />
              </ScatterChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <p className="p7-sub" style={{ marginTop: "1.5rem" }}>
        Served model: <span className="p7-mono">{metrics.modelName} · {metrics.modelVersion}</span>.
        Challenger scores (XGBoost, DistilBERT) live in <span className="p7-mono">ml/README.md</span>;
        they are not served because they cannot produce the exact coef×tfidf explanation the linear
        model gives.
      </p>
    </>
  );
}

function Stat({ value, label, sub, tone }: { value: string; label: string; sub?: string; tone?: string }) {
  return (
    <div className="p7-stat">
      <div className={`p7-stat-num ${tone ?? ""}`}>{value}</div>
      <span className="p7-stat-label">{label}</span>
      {sub && <span className="p7-stat-sub">{sub}</span>}
    </div>
  );
}

function fixed(x: number | null, digits: number): string {
  return x == null ? "—" : x.toFixed(digits);
}

const tooltipStyle: React.CSSProperties = {
  background: "var(--surface-raised)",
  border: "1px solid var(--border)",
  borderRadius: "10px",
  fontFamily: "var(--font-mono)",
  fontSize: "0.75rem",
  color: "var(--text)",
};
