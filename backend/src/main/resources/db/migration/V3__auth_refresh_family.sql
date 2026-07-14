-- V3__auth_refresh_family.sql — Phase 4: refresh-token rotation with reuse detection.
--
-- A refresh-token "family" is a rotation chain seeded at login. Every rotation issues a
-- new token in the same family and revokes its predecessor. If a token that has already
-- been rotated (i.e. revoked) is presented again, that is a replay: we revoke the ENTIRE
-- family, forcing re-authentication. This column is what makes that revocation possible.

ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid();

-- The default only exists to backfill any pre-existing rows (there are none in practice).
-- New rows must always carry an explicit family id, so drop the default to force the app
-- to be deliberate rather than silently minting singleton families.
ALTER TABLE refresh_tokens ALTER COLUMN family_id DROP DEFAULT;

-- Revoking a whole family on reuse is a keyed update; index the key.
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
