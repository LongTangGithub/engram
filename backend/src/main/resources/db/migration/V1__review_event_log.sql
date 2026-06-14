-- ENG-2: append-only review event log + derived projections.
-- Invariant: review_event is immutable after insert. Enforced here in the DB, not app convention.

-- ─── review_event ────────────────────────────────────────────────────────────
-- Every review is an immutable fact. Raw outcome is the source of truth;
-- fsrs_rating is a derived, versioned interpretation — never treat it as canonical on replay.
CREATE TABLE review_event (
    -- identity & ordering
    seq                     BIGSERIAL       NOT NULL,           -- stable insert-order tiebreaker
    event_id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_event_id         TEXT            NOT NULL,           -- caller-generated idempotency key
    user_id                 UUID            NOT NULL,
    concept_id              UUID            NOT NULL,
    occurred_at             TIMESTAMPTZ     NOT NULL,

    -- session context
    session_id              UUID,
    session_type            TEXT,
    format                  TEXT            NOT NULL,           -- cloze | mcq | free_recall | feynman
    response_latency_ms     INT,

    -- raw outcome (truth — never recalculated)
    is_correct              BOOLEAN         NOT NULL,
    score                   NUMERIC(4,3),                       -- 0.000–1.000, null if binary
    hint_used               BOOLEAN         NOT NULL DEFAULT false,

    -- derived grade (versioned interpretation of raw outcome)
    fsrs_rating             SMALLINT,                           -- 1=Again 2=Hard 3=Good 4=Easy
    grading_scheme_version  TEXT,
    expected_answer_ref     TEXT,
    grader_prompt_version   TEXT,
    model_id                TEXT,

    -- scheduler snapshot (carried through from event; FSRS math is ENG-5)
    stability_after         DOUBLE PRECISION,
    difficulty_after        DOUBLE PRECISION,
    due_at                  TIMESTAMPTZ,
    retrievability_at_review DOUBLE PRECISION,
    scheduler_version       TEXT,

    CONSTRAINT uq_user_client_event UNIQUE (user_id, client_event_id)
);

CREATE INDEX ix_review_event_concept ON review_event (concept_id, occurred_at, seq);
CREATE INDEX ix_review_event_user_time ON review_event (user_id, occurred_at DESC);

-- Append-only enforcement: reject any UPDATE or DELETE on review_event.
CREATE OR REPLACE FUNCTION fn_review_event_append_only()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'review_event is append-only: % on event_id=% is forbidden', TG_OP, OLD.event_id;
END;
$$;

CREATE TRIGGER trg_review_event_append_only
BEFORE UPDATE OR DELETE ON review_event
FOR EACH ROW EXECUTE FUNCTION fn_review_event_append_only();

-- ─── concept_scheduler_state ─────────────────────────────────────────────────
-- Derived projection for fast dashboard reads. Rebuildable by replaying review_event.
-- last_event_id = provenance: which event produced this state.
CREATE TABLE concept_scheduler_state (
    concept_id              UUID            PRIMARY KEY,
    user_id                 UUID            NOT NULL,

    -- scheduler fields (carried through from event snapshot; FSRS math is ENG-5)
    stability               DOUBLE PRECISION,
    difficulty              DOUBLE PRECISION,
    due_at                  TIMESTAMPTZ,
    retrievability_last     DOUBLE PRECISION,
    scheduler_version       TEXT,

    -- provenance
    last_event_id           UUID            REFERENCES review_event(event_id),
    last_reviewed_at        TIMESTAMPTZ,
    review_count            INT             NOT NULL DEFAULT 0,

    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_scheduler_user ON concept_scheduler_state (user_id, due_at);

-- ─── review_event_answer_payload ─────────────────────────────────────────────
-- Quarantined free-text answers. App-encrypted (bytea ciphertext).
-- Tombstoning nulls ciphertext while leaving the event row intact and queryable.
CREATE TABLE review_event_answer_payload (
    event_id                UUID            PRIMARY KEY REFERENCES review_event(event_id),
    user_id                 UUID            NOT NULL,

    ciphertext              BYTEA,                              -- null after tombstone
    encryption_key_id       TEXT            NOT NULL,

    retain_until            TIMESTAMPTZ     NOT NULL,
    tombstoned_at           TIMESTAMPTZ,                        -- set when ciphertext nulled

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);