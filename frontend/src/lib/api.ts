// Typed client for the Spring Boot analysis API. These types mirror
// com.scamshield.analysis.dto.AnalysisResponse exactly — the screen renders only what the
// backend returns.

export type Label = "LIKELY_SCAM" | "LIKELY_LEGITIMATE" | "UNCERTAIN";
export type AnalyzeKind = "POSTING" | "MESSAGE";

export type Contribution = { feature: string; contribution: number; charNgram: boolean };
export type PhraseHit = { phrase: string; category: string; weight: number; count: number };
export type Typosquat = { domain: string; legitimate: string; editDistance: number };
export type Salary = { amount: number; period: string; zScore: number; implausible: boolean };
export type SimilarScam = {
  id: number;
  textSnippet: string;
  source: string;
  similarity: number;
};

export type AnalysisResponse = {
  id: string;
  label: Label;
  probability: number;
  rawProbability: number | null;
  topContributions: Contribution[];
  matchedPhrases: PhraseHit[];
  typosquats: Typosquat[];
  salary: Salary | null;
  similarScams: SimilarScam[];
  latencyMs: number;
  cached: boolean;
  stageMillis: Record<string, number>;
  text: string;
  modelName: string;
  modelVersion: string;
  postingId: string;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function toApiError(res: Response): Promise<ApiError> {
  let message = `Request failed (${res.status})`;
  try {
    const body = await res.json();
    if (body && typeof body.message === "string") message = body.message;
  } catch {
    /* non-JSON error body */
  }
  return new ApiError(res.status, message);
}

export async function analyze(text: string, kind: AnalyzeKind): Promise<AnalysisResponse> {
  const res = await fetch(`${API_BASE}/api/v1/analysis`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, kind }),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function getAnalysis(id: string): Promise<AnalysisResponse> {
  const res = await fetch(`${API_BASE}/api/v1/analysis/${encodeURIComponent(id)}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- presentation helpers (pure formatting of API numbers, no invented data) ----

/** The confidence to show in the banner, derived from the calibrated P(scam). */
export function confidence(r: AnalysisResponse): number {
  if (r.label === "LIKELY_LEGITIMATE") return 1 - r.probability;
  return r.probability; // scam or uncertain: confidence in the scam reading
}

export function pct(x: number, digits = 0): string {
  return `${(x * 100).toFixed(digits)}%`;
}

export function signed(x: number, digits = 2): string {
  return `${x >= 0 ? "+" : "−"}${Math.abs(x).toFixed(digits)}`;
}

// ============================================================================
// Phase 7 — transparency + community. Every type mirrors a backend DTO exactly.
// ============================================================================

// ---- /model : model performance, all derived from stored validation predictions ----
export type OperatingPoint = { threshold: number; precision: number; recall: number };
export type PrPoint = { recall: number; precision: number };
export type RocPoint = { fpr: number; tpr: number };
export type CalibrationBin = { meanPredicted: number; empirical: number; count: number };
export type ThresholdPoint = {
  threshold: number;
  tp: number;
  fp: number;
  fn: number;
  tn: number;
  precision: number;
  recall: number;
};
export type ModelMetrics = {
  modelName: string;
  modelVersion: string;
  hasPredictions: boolean;
  total: number;
  positives: number;
  negatives: number;
  prAuc: number | null;
  rocAuc: number | null;
  brier: number | null;
  noSkillFloor: number | null;
  operating: OperatingPoint | null;
  pr: PrPoint[];
  roc: RocPoint[];
  calibration: CalibrationBin[];
  grid: ThresholdPoint[];
};

export async function getModelMetrics(): Promise<ModelMetrics> {
  const res = await fetch(`${API_BASE}/api/v1/models/active/metrics`, { cache: "no-store" });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- /trends : rising scam patterns from verdict_features aggregation ----
export type TrendPattern = {
  feature: string;
  charNgram: boolean;
  count: number;
  previousCount: number;
  delta: number;
  avgContribution: number;
};
export type Trends = { windowDays: number; from: string; to: string; patterns: TrendPattern[] };

export async function getTrends(window = "30d"): Promise<Trends> {
  const res = await fetch(`${API_BASE}/api/v1/trends?window=${encodeURIComponent(window)}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- /campaigns : Union-Find clusters of near-duplicate postings ----
export type CampaignSummary = {
  id: number;
  label: string;
  rootPostingId: string | null;
  memberCount: number;
  createdAt: string;
};
export type CampaignMember = { postingId: string; snippet: string; createdAt: string };
export type CampaignDetail = {
  id: number;
  label: string;
  memberCount: number;
  members: CampaignMember[];
};

export async function getCampaigns(): Promise<CampaignSummary[]> {
  const res = await fetch(`${API_BASE}/api/v1/campaigns`, { cache: "no-store" });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function getCampaign(id: string | number): Promise<CampaignDetail> {
  const res = await fetch(`${API_BASE}/api/v1/campaigns/${encodeURIComponent(String(id))}`, {
    cache: "no-store",
  });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- history (owner-only) : the caller's own past analyses, a projection of stored verdicts ----
export type AnalysisSummary = {
  id: string;
  postingId: string;
  label: Label;
  probability: number;
  source: string;
  snippet: string;
  createdAt: string;
};

// ---- bulk scan (owner-only) : a CSV of postings scored in one pass ----
export type BulkRow = {
  line: number;
  snippet: string;
  id: string;
  label: Label;
  probability: number;
};
export type BulkResult = {
  total: number;
  scam: number;
  uncertain: number;
  legit: number;
  rows: BulkRow[];
};

// ---- reports (authenticated) ----
export type Claim = "FALSE_POSITIVE" | "CONFIRMED_SCAM";
export type ReportSummary = {
  id: number;
  postingId: string;
  userId: number;
  claim: string;
  status: string;
  createdAt: string;
};

/** Human labels for the two confusion errors — used on /model. Kept here so the wording is one place. */
export const ERROR_LABELS = {
  falsePositive: "real jobs blocked",
  falseNegative: "scams let through",
} as const;
