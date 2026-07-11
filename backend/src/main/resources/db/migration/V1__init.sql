-- V1__init.sql — Scam Shield initial schema (Phase 1).
-- Target: PostgreSQL 16 with the pgvector extension.
-- Flyway owns the schema; the application runs with hibernate ddl-auto=validate
-- and never mutates DDL at runtime.
--
-- Conventions:
--   * Every table has created_at TIMESTAMPTZ NOT NULL DEFAULT now().
--   * User-facing, shareable rows (postings, verdicts) use UUID primary keys so
--     permalinks are not enumerable; internal tables use IDENTITY bigints.
--   * Enumerations are VARCHAR + CHECK so they map cleanly to @Enumerated(STRING).

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- Identity & auth
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,           -- BCrypt hash (~60 chars) + headroom
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER'
                       CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,               -- store only a hash, never the raw token
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- Model registry (declared before verdicts, which reference it)
-- ---------------------------------------------------------------------------
CREATE TABLE model_versions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    metrics       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    artifact_path VARCHAR(512),
    active        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_versions_name_version UNIQUE (name, version)
);
-- Enforce at most one active model version at any time.
CREATE UNIQUE INDEX uq_model_versions_single_active
    ON model_versions (active) WHERE active;

-- ---------------------------------------------------------------------------
-- Postings & verdicts
-- ---------------------------------------------------------------------------
CREATE TABLE postings (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      BIGINT      REFERENCES users (id) ON DELETE SET NULL,  -- nullable: anonymous allowed
    raw_text     TEXT        NOT NULL,
    source       VARCHAR(16) NOT NULL DEFAULT 'POSTING'
                     CHECK (source IN ('POSTING', 'MESSAGE')),          -- carries the API `kind`
    content_hash CHAR(64)    NOT NULL,                                  -- sha-256 hex of normalized text
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_postings_content_hash UNIQUE (content_hash)
);

CREATE TABLE verdicts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    posting_id       UUID         NOT NULL REFERENCES postings (id) ON DELETE CASCADE,
    model_version_id BIGINT       NOT NULL REFERENCES model_versions (id),
    probability      NUMERIC(7,6) NOT NULL CHECK (probability >= 0 AND probability <= 1),
    label            VARCHAR(24)  NOT NULL
                         CHECK (label IN ('LIKELY_SCAM', 'LIKELY_LEGITIMATE', 'UNCERTAIN')),
    latency_ms       INTEGER      NOT NULL CHECK (latency_ms >= 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_verdicts_posting    ON verdicts (posting_id);
CREATE INDEX idx_verdicts_created_at ON verdicts (created_at);

-- Per-feature log-odds contributions (coefficient * tfidf) that explain a verdict.
CREATE TABLE verdict_features (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    verdict_id   UUID          NOT NULL REFERENCES verdicts (id) ON DELETE CASCADE,
    feature_name VARCHAR(128)  NOT NULL,
    contribution NUMERIC(12,8) NOT NULL,             -- signed; log-odds space, not probability
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_verdict_features UNIQUE (verdict_id, feature_name)
);
CREATE INDEX idx_verdict_features_verdict ON verdict_features (verdict_id);

-- ---------------------------------------------------------------------------
-- Embeddings & known scams (vector search)
-- ---------------------------------------------------------------------------
CREATE TABLE posting_embeddings (
    posting_id UUID        PRIMARY KEY REFERENCES postings (id) ON DELETE CASCADE,
    embedding  vector(384) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_posting_embeddings_hnsw
    ON posting_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE known_scams (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text         TEXT        NOT NULL,
    embedding    vector(384) NOT NULL,
    source       VARCHAR(16) NOT NULL CHECK (source IN ('EMSCAD', 'COMMUNITY')),
    confirmed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_known_scams_hnsw
    ON known_scams USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------------------
-- Phrase registry (source for the Aho-Corasick pass)
-- ---------------------------------------------------------------------------
CREATE TABLE scam_phrases (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phrase     VARCHAR(255) NOT NULL,
    weight     NUMERIC(6,4) NOT NULL DEFAULT 0,
    category   VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_scam_phrases_phrase UNIQUE (phrase)
);

-- ---------------------------------------------------------------------------
-- Community reports & moderation
-- ---------------------------------------------------------------------------
CREATE TABLE reports (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    posting_id   UUID        NOT NULL REFERENCES postings (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claim        VARCHAR(20) NOT NULL CHECK (claim IN ('FALSE_POSITIVE', 'CONFIRMED_SCAM')),
    status       VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'MODERATOR_CONFIRMED', 'MODERATOR_REJECTED')),
    moderator_id BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reports_user_posting UNIQUE (user_id, posting_id)  -- one report per user per posting
);
CREATE INDEX idx_reports_posting ON reports (posting_id);
CREATE INDEX idx_reports_status  ON reports (status);

-- ---------------------------------------------------------------------------
-- Duplicate-campaign clustering (union-find output)
-- ---------------------------------------------------------------------------
CREATE TABLE campaigns (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label           VARCHAR(128),
    root_posting_id UUID    REFERENCES postings (id) ON DELETE SET NULL,
    member_count    INTEGER NOT NULL DEFAULT 0 CHECK (member_count >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaign_members (
    campaign_id BIGINT      NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    posting_id  UUID        NOT NULL REFERENCES postings (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (campaign_id, posting_id)
);
CREATE INDEX idx_campaign_members_posting ON campaign_members (posting_id);

-- ---------------------------------------------------------------------------
-- Drift monitoring
-- ---------------------------------------------------------------------------
CREATE TABLE drift_snapshots (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    window_end   TIMESTAMPTZ NOT NULL,
    metrics      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_drift_window CHECK (window_end >= window_start)
);

-- ---------------------------------------------------------------------------
-- Append-only audit log
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,  -- null = anonymous / system
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id   VARCHAR(64),                          -- holds a bigint or uuid as text
    ip          INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_actor      ON audit_log (actor_id);

-- Enforce append-only at the database layer. JPA/Hibernate cannot guarantee this,
-- so the invariant lives where it can actually be trusted.
CREATE OR REPLACE FUNCTION audit_log_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only; % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();
