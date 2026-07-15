-- V6__clerk_auth.sql — move authentication to Clerk.
--
-- Clerk owns identity (credentials, sessions, MFA) from here on. This app keeps a local `users`
-- row per Clerk user, provisioned on that user's first authenticated request and keyed by
-- `clerk_id`.
--
-- The local BIGINT `users.id` deliberately stays the primary key. Every foreign key in the schema
-- (postings.user_id, reports.user_id, reports.moderator_id, audit_log.actor_id) points at it, and
-- re-keying the table to Clerk's string ids would rewrite all of them for no benefit. Clerk's id
-- lives alongside as an alternate key.
--
-- Keeping the local row is not bookkeeping — two product guards depend on columns Clerk does not
-- give us on the request path:
--   * `created_at` powers the 7-day account-age rule that stops a scammer disputing their own
--     posting from a fresh account (ReportGuardIT.aTwoDayOldAccountCannotReport).
--   * `role` powers ADMIN-only retraining; authority is read from our database, never from a
--     client-supplied token claim.

ALTER TABLE users ADD COLUMN clerk_id VARCHAR(64);

-- Alternate key: one local row per Clerk user. Nullable so any pre-existing password rows survive
-- the migration; new rows always carry it (enforced in the app's provisioning path).
CREATE UNIQUE INDEX uq_users_clerk_id ON users (clerk_id) WHERE clerk_id IS NOT NULL;

-- Clerk stores credentials now; we must not. Existing hashes are dropped rather than left to rot:
-- a password hash we no longer authenticate against is pure liability if the database leaks.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
UPDATE users SET password_hash = NULL;

-- Clerk verifies email addresses; the local flag would be a stale copy of its truth.
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT TRUE;

-- Clerk owns sessions, so rotation and reuse detection move to it. This table (and V3's family
-- tracking) has no reader left.
DROP TABLE IF EXISTS refresh_tokens;
