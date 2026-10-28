CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- Users & roles
-- ---------------------------------------------------------------------------
CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- ---------------------------------------------------------------------------
-- Documents: one row per ingested file / wiki page.
-- allowed_roles drives RBAC: a user may read a document when their roles
-- overlap with allowed_roles (ADMIN bypasses the check).
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(255)  NOT NULL,
    filename           VARCHAR(255)  NOT NULL,
    source_type        VARCHAR(32)   NOT NULL,
    source_url         TEXT,
    content_hash       VARCHAR(64)   NOT NULL UNIQUE,
    size_bytes         BIGINT        NOT NULL,
    allowed_roles      TEXT[]        NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    page_count         INT,
    chunk_count        INT           NOT NULL DEFAULT 0,
    token_count        INT           NOT NULL DEFAULT 0,
    embedding_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    error_message      TEXT,
    uploaded_by        VARCHAR(100),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT documents_roles_not_empty CHECK (cardinality(allowed_roles) > 0)
);

CREATE INDEX idx_documents_allowed_roles ON documents USING gin (allowed_roles);
CREATE INDEX idx_documents_status ON documents (status);

-- ---------------------------------------------------------------------------
-- Chunks: embeddings for semantic search + tsvector for keyword search.
-- Section headings are weighted higher than body text for keyword ranking.
-- ---------------------------------------------------------------------------
CREATE TABLE document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    page_number INT,
    section     TEXT,
    token_count INT          NOT NULL,
    embedding   vector(1536) NOT NULL,
    content_tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(section, '')), 'A') ||
        setweight(to_tsvector('english', content), 'B')
    ) STORED,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_embedding_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_tsv ON document_chunks USING gin (content_tsv);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

-- ---------------------------------------------------------------------------
-- Observability: one row per question answered (cost, latency, grounding)
-- ---------------------------------------------------------------------------
CREATE TABLE query_logs (
    id                BIGSERIAL PRIMARY KEY,
    username          VARCHAR(100)   NOT NULL,
    question          TEXT           NOT NULL,
    answer            TEXT,
    grounded          BOOLEAN        NOT NULL,
    cache_hit         BOOLEAN        NOT NULL,
    sources_count     INT            NOT NULL,
    prompt_tokens     INT            NOT NULL DEFAULT 0,
    completion_tokens INT            NOT NULL DEFAULT 0,
    embedding_tokens  INT            NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(12, 6) NOT NULL DEFAULT 0,
    latency_ms        BIGINT         NOT NULL,
    retrieval_ms      BIGINT         NOT NULL DEFAULT 0,
    generation_ms     BIGINT         NOT NULL DEFAULT 0,
    model             VARCHAR(64),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_logs_created_at ON query_logs (created_at);

-- ---------------------------------------------------------------------------
-- Evaluation runs against the golden dataset
-- ---------------------------------------------------------------------------
CREATE TABLE eval_runs (
    id               BIGSERIAL PRIMARY KEY,
    case_count       INT              NOT NULL,
    hit_rate         DOUBLE PRECISION NOT NULL,
    mrr              DOUBLE PRECISION NOT NULL,
    keyword_recall   DOUBLE PRECISION NOT NULL,
    citation_rate    DOUBLE PRECISION NOT NULL,
    refusal_accuracy DOUBLE PRECISION NOT NULL,
    leakage_count    INT              NOT NULL,
    faithfulness     DOUBLE PRECISION,
    avg_latency_ms   DOUBLE PRECISION NOT NULL,
    total_cost_usd   NUMERIC(12, 6)   NOT NULL,
    results_json     TEXT             NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);
