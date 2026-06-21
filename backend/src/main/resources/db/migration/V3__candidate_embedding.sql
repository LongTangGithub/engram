-- ENG-8 prereq: candidate vectors for pgvector kNN.
-- pgvector extension: safe to run even if already enabled (e.g. by V1).
CREATE EXTENSION IF NOT EXISTS vector;

-- embedding        : the candidate's vector representation (text-embedding-3-small = 1536 dims).
--                    Nullable — candidates exist before embedding; backfill fills them.
-- embedding_model  : which model produced the vector (e.g. "text-embedding-3-small").
--                    Lets us detect stale embeddings if the model ever changes.
-- embedded_at      : when the vector was last written.
ALTER TABLE concept_candidate
    ADD COLUMN IF NOT EXISTS embedding       vector(1536),
    ADD COLUMN IF NOT EXISTS embedding_model TEXT,
    ADD COLUMN IF NOT EXISTS embedded_at     TIMESTAMPTZ;

-- HNSW index with cosine ops.
-- HNSW (not IVFFlat): IVFFlat's k-means training on a near-empty table silently destroys recall.
-- HNSW builds correctly on an empty/growing table — no training step required.
-- ef_search can be tuned at query time via SET hnsw.ef_search for recall/speed tradeoffs.
CREATE INDEX IF NOT EXISTS concept_candidate_embedding_hnsw
    ON concept_candidate
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
