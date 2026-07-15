"use client";

// Session handling on top of Clerk. Clerk owns identity: it holds the credentials, the session,
// and the token. This module does two things with that — turns the live session into a bearer
// token for the Spring API, and resolves the caller's local account (id and role) from that API.
//
// Roles are NOT read from the Clerk token. The API decides what a caller is, from its own database.

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

/**
 * The Clerk singleton ClerkProvider puts on `window`. Reached directly, rather than through
 * `useAuth()`, so the data functions below stay plain async calls that any component can make —
 * a hook would force every caller to be rewritten around it.
 */
type ClerkGlobal = {
  loaded?: boolean;
  load?: () => Promise<unknown>;
  session?: { getToken: () => Promise<string | null> } | null;
};

/** The live session token, or null when nobody is signed in. Clerk refreshes it for us. */
async function sessionToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  const clerk = (window as Window & { Clerk?: ClerkGlobal }).Clerk;
  if (!clerk) return null;
  // A call can land before ClerkJS finishes booting; load() is idempotent and resolves immediately
  // once it has.
  if (!clerk.loaded) await clerk.load?.();
  return (await clerk.session?.getToken()) ?? null;
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

/** A fetch that carries the Clerk session token. Throws ApiError(401) when there is no session. */
export async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = await sessionToken();
  if (!token) throw new ApiError(401, "Please sign in to continue.");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

/**
 * The caller's local account. The id here is this application's own `users.id`, not Clerk's — it is
 * what every posting, report and audit row is keyed by. The row is created by the API on the first
 * authenticated request, so this is also what provisions a brand-new Clerk user.
 */
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

// ---- React hook: current session, reactive to Clerk sign-in/out --------------------------

import { useAuth } from "@clerk/nextjs";
import { useCallback, useEffect, useState } from "react";

export type Session = {
  /** The local account, or null when signed out — or when `error` is set. */
  me: Me | null;
  loading: boolean;
  /**
   * Clerk's answer to "is there a session?", independent of whether our API could be reached.
   * Distinct from `me` on purpose: a caller can be genuinely signed in while the API is down, and
   * treating that as signed-out sends them to /login, which Clerk bounces straight back — a loop.
   */
  signedIn: boolean;
  /** Set only when there IS a session but the API could not say who it belongs to. */
  error: Error | null;
  refresh: () => void;
};

/**
 * The signed-in user's local account, or null.
 *
 * Two sources, deliberately: Clerk answers "is there a session?", and our API answers "who is that
 * here, and what may they do?". The role must come from the API — a role claim in a client-held
 * token is not something to trust.
 */
export function useSession(): Session {
  const { isLoaded, isSignedIn } = useAuth();
  const [meState, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!isLoaded) return; // Clerk still booting; stay in the loading state
    if (!isSignedIn) {
      setMe(null);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    me()
      .then((account) => {
        setMe(account);
        setError(null);
      })
      .catch((cause: unknown) => {
        // Kept, not swallowed. A failure here means the API rejected a live Clerk session — a
        // misconfigured issuer, or a sleeping instance — and the user is owed that, not a silent
        // demotion to "signed out".
        setMe(null);
        setError(cause instanceof Error ? cause : new Error(String(cause)));
      })
      .finally(() => setLoading(false));
  }, [isLoaded, isSignedIn]);

  useEffect(load, [load]);

  return {
    me: meState,
    loading: loading || !isLoaded,
    signedIn: Boolean(isSignedIn),
    error,
    refresh: load,
  };
}
