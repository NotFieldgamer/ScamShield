-- V5__remove_moderator_role.sql — collapse the two-tier privilege model into ADMIN only.
--
-- The MODERATOR role is removed: every privileged (/api/v1/admin/**) route now requires ADMIN.
-- Any existing MODERATOR account is promoted to ADMIN so it keeps its access, then the role CHECK
-- constraint is narrowed to ('USER', 'ADMIN').
--
-- NOTE: the report *status* vocabulary (MODERATOR_CONFIRMED / MODERATOR_REJECTED) and the
-- reports.moderator_id column are data-model markers for "a privileged human decided this"; they
-- are deliberately left unchanged — they name the decision, not the (now removed) role.

UPDATE users SET role = 'ADMIN' WHERE role = 'MODERATOR';

ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'ADMIN'));
