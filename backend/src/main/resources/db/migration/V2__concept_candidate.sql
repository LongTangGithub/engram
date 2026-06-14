-- Mutable table: candidates are updated/deleted as the vault changes.
-- No append-only trigger — lifecycle state and content must be editable.
CREATE TABLE concept_candidate (
    concept_id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL,
    source_type         TEXT        NOT NULL,
    source_ref          TEXT        NOT NULL,
    source_content_hash TEXT        NOT NULL,
    title               TEXT        NOT NULL,
    topic_tag           TEXT,
    source_span         TEXT,
    lifecycle_state     TEXT        NOT NULL DEFAULT 'CANDIDATE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concept_candidate_pk PRIMARY KEY (concept_id),
    CONSTRAINT concept_candidate_unique UNIQUE (user_id, source_type, source_ref, title)
);

CREATE INDEX concept_candidate_user_source
    ON concept_candidate (user_id, source_type, source_ref);
