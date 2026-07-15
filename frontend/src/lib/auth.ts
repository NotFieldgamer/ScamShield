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

/**
 * Auth calls go to our own origin, which proxies them to the API (see next.config.mjs). That is
 * what lets the API's SameSite=Strict refresh cookie work: the browser sees it set by the page's
 * own origin. Everything else calls the API directly via API_BASE.
 */
const AUTH = "/api/v1/auth";

/**
 * @param notify whether to wake session listeners. A silent token renewal must not: it does not
 *   change who is signed in, and re-entering the listener would re-fetch the session for nothing.
 */
function store(token: string, expiresInSeconds: number, notify = true) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXP_KEY, String(Date.now() + expiresInSeconds * 1000));
  if (notify) window.dispatchEvent(new Event(EVENT));
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
  const res = await fetch(`${AUTH}/login`, {
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
  const res = await fetch(`${AUTH}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toApiError(res);
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${AUTH}/logout`, { method: "POST", credentials: "include" });
  } finally {
    clear();
  }
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Trades the httpOnly refresh cookie for a new access token.
 *
 * Single-flighted deliberately. The API rotates the refresh token on every use and treats a
 * second use of the old one as theft, revoking the whole token family. Two components refreshing
 * at once would look exactly like that and sign the user out.
 */
function renewAccessToken(): Promise<boolean> {
  refreshInFlight ??= requestRenewal().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function requestRenewal(): Promise<boolean> {
  let res: Response;
  try {
    res = await fetch(`${AUTH}/refresh`, { method: "POST", credentials: "include" });
  } catch {
    return false; // offline or the API is asleep: keep the session and let the caller retry
  }
  if (!res.ok) {
    clear(); // the cookie is gone, expired, or revoked — this session is genuinely over
    return false;
  }
  const body = (await res.json()) as { accessToken: string; expiresInSeconds: number };
  store(body.accessToken, body.expiresInSeconds, false);
  return true;
}

/**
 * The live access token, renewed from the refresh cookie when the current one has expired.
 * Returns null when nobody is signed in. Anonymous visitors never reach the API: with no prior
 * session there is no cookie to trade.
 */
export async function ensureToken(): Promise<string | null> {
  const token = getToken();
  if (token) return token;
  if (typeof window === "undefined" || localStorage.getItem(EXP_KEY) === null) return null;
  return (await renewAccessToken()) ? getToken() : null;
}

/** A fetch that carries the bearer token. Throws ApiError(401) when there is no live session. */
export async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = await ensureToken();
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
    setLoading(true);
    // ensureToken, not getToken: on a reload or after the 15-minute access token expires, the
    // refresh cookie can still restore the session. Checking only the stored token would sign
    // the user out of a session the API still considers live.
    ensureToken()
      .then((token) => (token ? me() : null))
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
