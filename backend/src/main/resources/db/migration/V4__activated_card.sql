-- ENG-8a: activated_card — generate-once, idempotent storage for AI-generated MCQ cards.
-- activated_at on concept_candidate is ORTHOGONAL to lifecycle_state/SEEDED — independent axes.

CREATE TABLE activated_card (
    card_id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    concept_id                UUID        NOT NULL UNIQUE,
    user_id                   UUID        NOT NULL,
    question                  TEXT        NOT NULL,
    correct_answer            TEXT        NOT NULL,
    distractors               JSONB       NOT NULL,
    generation_model          TEXT        NOT NULL,
    generation_prompt_version TEXT        NOT NULL,
    input_tokens              INT         NOT NULL DEFAULT 0,
    output_tokens             INT         NOT NULL DEFAULT 0,
    cost_micros               BIGINT      NOT NULL DEFAULT 0,
    idempotency_key           TEXT        NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- activated_at: when the AI card was generated. Nullable — concepts exist before activation.
-- ORTHOGONAL to lifecycle_state: a concept can be SEEDED before activated, or CANDIDATE after.
ALTER TABLE concept_candidate
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ;
