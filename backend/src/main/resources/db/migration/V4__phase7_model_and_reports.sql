-- V4__phase7_model_and_reports.sql — Phase 7 (transparency + community).
--
-- Two changes:
--   1. validation_predictions: the per-example (y_true, y_score) held-out predictions the
--      /model page recomputes precision/recall/FP/FN from as the threshold slider moves. This
--      is the single stored source of truth; every curve and confusion count is derived from
--      it in Java, so no number on /model is hard-coded. The rows are produced offline by
--      ml/notebooks/02_train_classifier.ipynb (the untouched test split) and loaded like the
--      known_scams seed — never fabricated here.
--   2. A COMMUNITY_CONFIRMED report status: two independent reporters agreeing flips the
--      community-visible label, but this status is deliberately NOT what retraining reads.
--      Retraining consumes only MODERATOR_CONFIRMED (brief §H), so a scammer self-reporting
--      cannot poison the training set even with two sock-puppet accounts.

CREATE TABLE validation_predictions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_version_id BIGINT       NOT NULL REFERENCES model_versions (id) ON DELETE CASCADE,
    y_true           SMALLINT     NOT NULL CHECK (y_true IN (0, 1)),   -- 1 = fraud (positive class)
    y_score          NUMERIC(7,6) NOT NULL CHECK (y_score >= 0 AND y_score <= 1),
    split            VARCHAR(8)   NOT NULL DEFAULT 'TEST'
                         CHECK (split IN ('TEST', 'CALIB')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_validation_predictions_model ON validation_predictions (model_version_id);

-- Widen the report-status vocabulary with the community-consensus state. The existing CHECK
-- constraint is replaced, not dropped, so PENDING/MODERATOR_CONFIRMED/MODERATOR_REJECTED remain
-- valid. COMMUNITY_CONFIRMED is a moderation *hint*, not a training signal.
ALTER TABLE reports DROP CONSTRAINT reports_status_check;
ALTER TABLE reports ADD CONSTRAINT reports_status_check
    CHECK (status IN ('PENDING', 'COMMUNITY_CONFIRMED', 'MODERATOR_CONFIRMED', 'MODERATOR_REJECTED'));
