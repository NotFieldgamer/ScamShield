"use client";

// Minimal client-side session: the short-lived access token is kept in localStorage and sent as
// a bearer header; the refresh token lives only in an httpOnly cookie the backend set, which JS
// can never read. This owns none of the cryptography — it just carries the token the API issued.

import {
  API_BASE,
  ApiError,
  type AnalysisSummary,
  type BulkResult,
  type Claim,
  type ReportSummary,
} from "@/lib/api";

export type Role = "USER" | "ADMIN";
export type Me = { id: number; email: string; role: Role; emailVerified: boolean };

const TOKEN_KEY = "ss_access_token";
const EXP_KEY = "ss_token_expiry";
const EVENT = "ss-auth-change";

function store(token: string, expiresInSeconds: number) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXP_KEY, String(Date.now() + expiresInSeconds * 1000));
  window.dispatchEvent(new Event(EVENT));
}

function clear() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EXP_KEY);
  window.dispatchEvent(new Event(EVENT));
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  const token = localStorage.getItem(TOKEN_KEY);
  const exp = Number(localStorage.getItem(EXP_KEY) ?? 0);
  if (!token || Date.now() >= exp) return null;
  return token;
}

async function toApiError(res: Response): Promise<ApiError> {
  let message = `Request failed (${res.status})`;
  try {
    const body = await res.json();
    if (body && typeof body.message === "string") message = body.message;
  } catch {
    /* non-JSON */
  }
  return new ApiError(res.status, message);
}

export async function login(email: string, password: string): Promise<Me> {
  const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // accept the httpOnly refresh cookie
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toApiError(res);
  const body = (await res.json()) as { accessToken: string; expiresInSeconds: number };
  store(body.accessToken, body.expiresInSeconds);
  return me();
}

export async function register(email: string, password: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/v1/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toApiError(res);
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_BASE}/api/v1/auth/logout`, { method: "POST", credentials: "include" });
  } finally {
    clear();
  }
}

/** A fetch that carries the bearer token. Throws ApiError(401) when there is no live session. */
export async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  if (!token) throw new ApiError(401, "Please sign in to continue.");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

export async function me(): Promise<Me> {
  const res = await authedFetch("/api/v1/me");
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- authenticated API calls -------------------------------------------------------------

/** The caller's own analysis history (owner-only). Requires a live session; the API scopes to them. */
export async function getMyAnalyses(limit = 100): Promise<AnalysisSummary[]> {
  const res = await authedFetch(`/api/v1/me/analyses?limit=${limit}`);
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

/**
 * Bulk-scan a CSV of postings (owner-only). The browser sets the multipart boundary itself, so we
 * pass FormData and let authedFetch add only the bearer header.
 */
export async function analyzeBulk(file: File): Promise<BulkResult> {
  const form = new FormData();
  form.append("file", file);
  const res = await authedFetch("/api/v1/analysis/bulk", { method: "POST", body: form });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function submitReport(postingId: string, claim: Claim): Promise<ReportSummary> {
  const res = await authedFetch("/api/v1/reports", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ postingId, claim }),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function pendingReports(): Promise<ReportSummary[]> {
  const res = await authedFetch("/api/v1/admin/reports");
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function resolveReport(id: number, decision: "CONFIRM" | "REJECT"): Promise<void> {
  const res = await authedFetch(`/api/v1/admin/reports/${id}/resolve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ decision }),
  });
  if (!res.ok) throw await toApiError(res);
}

export type AuditEntry = {
  id: number;
  actorId: number | null;
  action: string;
  targetType: string;
  targetId: string | null;
  ip: string | null;
  createdAt: string;
};

export async function auditLog(limit = 100): Promise<AuditEntry[]> {
  const res = await authedFetch(`/api/v1/admin/audit?limit=${limit}`);
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

export async function reclusterCampaigns(): Promise<{ campaigns: number }> {
  const res = await authedFetch("/api/v1/admin/campaigns/recluster", { method: "POST" });
  if (!res.ok) throw await toApiError(res);
  return res.json();
}

// ---- React hook: current session, reactive to login/logout ------------------------------

import { useEffect, useState } from "react";

export function useSession(): { me: Me | null; loading: boolean; refresh: () => void } {
  const [meState, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    if (!getToken()) {
      setMe(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    me()
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    const onChange = () => load();
    window.addEventListener(EVENT, onChange);
    return () => window.removeEventListener(EVENT, onChange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { me: meState, loading, refresh: load };
}
