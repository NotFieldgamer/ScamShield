# Security — Scam Shield

_Last reviewed: 2026-07-14._

Scam Shield owns its UI, but not its security primitives. Authentication uses Spring Security;
passwords use BCrypt; tokens use a maintained JWT library (`jjwt`). We never hand-roll a token
parser, a password hasher, or a session scheme. This document covers the auth design, the
feedback-loop poisoning guards, and the known limitations we have chosen not to fix.

---

## Authentication and sessions

Stateless, bearer-token, no server sessions.

- **Access token.** A JWT (HS256), **15-minute** lifetime, sent in the `Authorization: Bearer`
  header. It carries the user id (subject) and role. The role authority comes from the signed
  token, never from a client-supplied header. An expired or tampered token leaves the security
  context empty; protected routes then return 401. _(Proven: `AuthFlowIT.anExpiredAccessTokenIsRejected`.)_
- **Refresh token.** A 256-bit random string, **hashed (SHA-256) at rest** so a database leak does
  not expose usable tokens. It lives only in an **httpOnly, Secure, SameSite=Strict cookie** scoped
  to `/api/v1/auth`, so client JavaScript can never read it and it cannot be driven cross-site.
  7-day lifetime, **rotating and single-use.**
  A `Strict` cookie is only returned to the site that set it, so the frontend proxies
  `/api/v1/auth/*` through its own origin (a Next.js rewrite) and the browser holds the cookie as
  first-party. Without that proxy the cookie would be set on the API's origin and never sent back,
  and `Strict` would have to be weakened to `None` to work at all. The proxy covers the auth routes
  **only**: every other call goes directly to the API with a bearer header, preserving the caller's
  IP for the analysis rate limit.
- **Reuse detection.** Presenting an already-rotated refresh token is treated as replay: the entire
  token **family is revoked** and the request is rejected. Rotation is made atomic against
  concurrent replay by a conditional revoke (`UPDATE … WHERE id = ? AND revoked_at IS NULL`); if a
  racing request already rotated the token, this affects zero rows and is treated exactly like
  reuse. _(Proven: `AuthFlowIT.aRotatedRefreshTokenCannotBeReused`,
  `reuseOfARotatedTokenRevokesTheWholeFamily`, `concurrentRotationOfTheSameTokenIsRejectedByTheConditionalRevoke`.)_
- **Passwords.** BCrypt at cost **12**. Comparison is done by Spring Security's provider, never by
  our code. Login returns one message for both "no such user" and "wrong password" so it does not
  reveal which.

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
