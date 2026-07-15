# Security — Verity

_Last reviewed: 2026-07-15._

Verity does not hand-roll security primitives. **Identity is Clerk's**: it holds the credentials,
runs the sign-in flow, and issues the session tokens. This API never sees a password and stores no
signing key. Verification is Spring Security's OAuth2 resource server — it fetches Clerk's JWKS,
follows key rotation, and checks signature, expiry and issuer. We wrote none of that.

What we do own is **authorisation**: who a caller is *here*, and what they may do. That is decided
from our own database, never from a claim in a client-held token.

This document covers the auth design, the feedback-loop poisoning guards, and the known limitations
we have chosen not to fix.

---

## Authentication and sessions

Stateless, bearer-token, no server sessions. **Clerk issues; we verify.** (Migrated from local
password auth on 2026-07-15; see V6.)

- **Session token.** A Clerk-issued JWT (**RS256**), sent in the `Authorization: Bearer` header and
  refreshed by Clerk's SDK in the browser. This API verifies it as an OAuth2 resource server and
  holds **no signing key at all** — only a public one it fetches. An expired or tampered token
  leaves the security context empty; protected routes then return 401.
  _(Proven: `ClerkAuthIT.anExpiredTokenIsRejected`, `aTokenSignedByAnyoneElseIsRejected`.)_
- **Key handling.** Keys come from Clerk's JWKS, fetched lazily on the first token, then cached, with
  rotation followed automatically (`SecurityConfig.jwtDecoder`). Lazily on purpose: the eager
  `withIssuerLocation` form resolves the discovery document inside `build()`, which would make every
  cold start — and the whole application context — depend on Clerk being reachable at that instant.
- **Issuer pinning.** Every token's `iss` must equal our configured `CLERK_ISSUER`. Signature alone
  only proves *some* Clerk instance minted the token; the issuer check is what makes a token from
  anybody else's Clerk instance useless here.
- **Credentials, rotation, reuse detection, MFA.** Clerk's, now. It stores no password of ours
  because we never receive one. The refresh-token family and its reuse detection (V1/V3) were
  dropped in V6 along with the `refresh_tokens` table; there is no longer a token of ours to replay.
- **No cookie of ours.** Clerk's session cookie lives on the frontend origin. This API is reached
  only by bearer header, which is non-ambient and so cannot be driven by CSRF — which is why the
  filter chain disables CSRF and why the old `/api/v1/auth/*` proxy (a workaround for a
  `SameSite=Strict` cookie across two sites) no longer exists.
- **Trust boundary — roles are ours.** A Clerk token can carry arbitrary custom claims, so a `role`
  claim is attacker-influenceable and is never read. `ClerkJwtAuthenticationConverter` resolves the
  token's subject to a local `users` row and takes the role **from the database**. A new Clerk user
  is always provisioned `USER`; only a decision recorded here makes an `ADMIN`.
  _(Proven: `ClerkAuthIT.everyAdminRouteRequiresTheAdminRole`,
  `anAdminRoleInTheDatabaseNotTheTokenIsWhatGrantsAccess`.)_
- **Email is read server-to-server.** Clerk's default token carries no email, and the browser's word
  for it would be a claim about identity — the very thing being verified. It is fetched once from
  Clerk's Backend API when the local row is provisioned (`ClerkApi.primaryEmail`), then stored.

## Authorization

- **Roles:** `USER`, `ADMIN` (the `MODERATOR` role was removed in V5; every privileged route is
  ADMIN-only). Every `/api/v1/admin/**` route requires the **`ADMIN`** role, enforced two ways: a
  method-level `@PreAuthorize("hasRole('ADMIN')")` (via `@EnableMethodSecurity`) and the filter
  chain, which gates `/api/v1/admin/**` with `hasRole('ADMIN')` — defense in depth, so a route that
  ever forgets the annotation still cannot be reached by a non-admin. A `USER` receives **403** on
  every admin route. _(Proven: `AuthFlowIT.everyAdminRouteRequiresTheAdminRole`.)_
- Transparency reads (`/model`, `/trends`, `/campaigns`) are public GETs by design.

## Platform hardening

- **No secrets in the repository.** `application.yml` reads from environment variables; the app
  **refuses to start without `JWT_SECRET`** (no default signing key). `.gitignore` excludes
  `**/kaggle.json`, `.env`, and `*.onnx`.
- **CORS** is restricted to the single configured frontend origin, with credentials allowed only so
  the refresh cookie works cross-origin.
- **Global exception handler** turns every exception into clean JSON and **never returns a stack
  trace.** Client errors (malformed body, bad param, denied authorization) map to 4xx explicitly so
  they are never reported as 500. _(Proven: `AuthFlowIT.aMalformedLoginBodyReturns400NotAServerError`.)_
- **Actuator** exposes only `health` and `info`, and `info` does **not** expose OS/Java versions
  (reconnaissance material, CWE-200).
- **Rate limiting.** A Redis token-bucket filter keys on the caller's IP (for both anonymous and
  authenticated callers); the check-and-decrement is a single atomic Lua script. It **fails open**
  if Redis is unreachable — a public analyzer should not 500 because the rate-limit store blipped.
- **Content-hash dedup.** Identical postings return the cached verdict, which also blunts repeated
  submission as an abuse vector.

---

## The feedback loop is an attack surface

Users can report a verdict as wrong. **A scammer will report their own scam as legitimate.** The
strongest engineering signal in this project is that the correction channel is designed against
exactly that. Three guards:

1. **Account age.** Reporting requires an account **at least 7 days old**. A scammer cannot spin up
   a fresh account to dispute their own posting. _(Proven: `ReportGuardIT.aTwoDayOldAccountCannotReport`
   returns 403; `anEstablishedAccountCanReport` returns 201.)_
2. **Two-reporter agreement.** The community-visible label flips only when **two independent
   accounts** agree — a single report changes nothing. Agreement moves the reports to a
   `COMMUNITY_CONFIRMED` state. _(Proven:
   `ReportGuardIT.twoIndependentReportersFlipTheCommunityLabelButNotTheTrainingSet`.)_
3. **Admin-only retraining.** `COMMUNITY_CONFIRMED` is a moderation _hint_, never a training
   signal. Retraining reads **only** reports with status `MODERATOR_CONFIRMED`
   (`ReportRepository.moderatorConfirmed()`) — a status only an `ADMIN` decision can set. So even
   two sock-puppet accounts cannot reach the training set; only an admin's explicit decision can.
   _(Proven: `ReportGuardIT.onlyAnAdminDecisionReachesTheTrainingSet`.)_

Every state change — login, refresh, reuse detection, report, moderation decision, reclustering —
writes to an **append-only `audit_log`**. Append-only is enforced at the database with a trigger
that rejects `UPDATE`/`DELETE`, because JPA cannot guarantee it and the invariant must live where it
can actually be trusted.

---

## Adversarial review

Phase 7 was reviewed by a multi-agent adversarial pass (independent finders per dimension, each
finding then verified by a separate skeptic). Confirmed issues that were **fixed**:

- `/confusion` returned a fabricated `precision = 1.0` when no predictions were loaded → now reports
  an honest empty state (`hasPredictions: false`).
- A malformed client `X-Forwarded-For` value could crash the `CAST(? AS inet)` audit insert and roll
  back the audited action → the IP is sanitized to null unless it is a valid literal
  (`AuditRepository.sanitizeIp`, unit-tested).
- Campaign reclustering wrote its audit row outside the rebuild transaction → the rebuild and the
  audit row now commit atomically (`@Transactional`).
- The near-duplicate self-join was unbounded and non-deterministic → bounded to the most recent
  postings with a deterministic order.

### Known limitations (documented, not fixed)

Transparency over cover-up:

- **X-Forwarded-For is client-controlled.** The audit-log IP is taken from the leftmost
  `X-Forwarded-For` token, which a client can forge. This is a **deployment hardening** item: the
  reverse proxy in front of the app (Render's router) should overwrite or strip
  inbound `X-Forwarded-For` so only the proxy-appended, trustworthy client IP reaches the app. It
  does not defeat any of the three
  feedback-loop guards; it only weakens IP attribution in the audit trail. (The crash path above is
  fixed regardless.)
- **Community-label promotion is best-effort under concurrency.** Under PostgreSQL's default READ
  COMMITTED isolation, two simultaneous "second reporter" submissions can each fail to see the
  other and skip the community-label flip. This delays a community label; it never grants an
  unauthorized flip, and it does not affect the admin-only retraining guarantee. A stronger fix
  (advisory lock or SERIALIZABLE on submission) is deferred.

---

## Reporting a vulnerability

This is a portfolio project, not a production service. If you find a security issue, open an issue
describing it. Do not include exploit details for anything that could affect real users of a live
deployment.
