# Engram V1 — System Design & Decisions

The committed source of truth. Mirrors the Notion page; lives in-repo so it can't drift and Claude
Code reads it every session. Calibration throughout: **the middle lane.**

---

## 1. Problem & persona
- **Problem — the collector's fallacy.** People hoard notes and never convert them into durable
  memory. Engram turns a static second brain into active recall.
- **V1 persona — PKM power users** (Notion / Obsidian power users, BASB-literate). Programmers-
  learning are a *flavor* of this audience, not a separate build target.
- **Positioning —** "Readwise for active recall."

## 2. The locked decisions
1. **Format selection, not exam-mimicry.** AI adapts generation schema + UI to a chosen *format*.
   V1 set: cloze, multiple choice, free recall, Feynman.
2. **Generation pipeline.** Extractor → Professor → Distractor, fixed order, structured JSON. Atomic
   unit = a concept card. RAG does two jobs: grounds the Professor in real notes, and sources the
   Distractor's wrong answers from neighboring concepts in the user's own vault.
3. **Hybrid extraction timing.** Cheap candidate pass at import; expensive deep generation only
   on-demand at quiz time, then persisted.
4. **Candidate → Activated → Seeded lifecycle.** Import creates lightweight *candidate* concepts
   (title, tag, source pointer, embedding) — enough to render a health bar. First quiz *activates*
   a candidate into a full card. A concept becomes *seeded* on its first self-graded review.
5. **FSRS as the decay engine, behind a swappable `retrievability()` interface.** Predicted
   retrievability = the health bar. Restoration scales with retrieval difficulty. Layer-2 semantic
   priors are the earmarked first upgrade, not launched.
6. **Append-only event log as the foundation.** Every review is an immutable fact. FSRS state is a
   derived projection, rebuildable by replay. Raw outcome = truth; the 1–4 grade is a versioned
   interpretation (`grading_scheme_version`, `scheduler_version`).
7. **Persist `user_answer` as a quarantined payload.** Free-recall/Feynman text in a separate,
   encrypted, tombstone-able table keyed by `event_id`, never touched by dashboard queries, with a
   productized retention policy. Grading context captured too (`expected_answer_ref`,
   `grading_rubric_version`/`grader_prompt_version`, `model_id`).
8. **Input layer.** Notion live-sync + Obsidian via folder ingest; Obsidian auto-sync plugin as
   fast-follow. One Source Adapter → one `IngestedDocument`. "MCP" = sync-and-index transport, not
   a live generation-time query layer.

## 3. System design
Core loop: `Source → Source Adapter → cheap candidate extraction → instant living dashboard →
quiz request → deep activation → review event → FSRS update → decay / restore`.

Generation: deterministic Extract → ground → Professor → Distractor → verify. Structured JSON, no
agent loops, no fine-tuning. Model tiering: cheap (Haiku) for Extractor/candidate/cloze/grading/
Professor; expensive (Sonnet) for the Distractor only.

### Data model (Postgres)
- `review_event` — append-only, immutable. Identity & time (`event_id`, `client_event_id`
  idempotency key, `user_id`, `concept_id`, `occurred_at`); context (`session_id`, `session_type`,
  `format`, `response_latency_ms`); raw outcome (`is_correct`, `score`, `hint_used`); derived grade
  (`fsrs_rating`, `grading_scheme_version`, `expected_answer_ref`, `grader_prompt_version`,
  `model_id`); scheduler snapshot (`stability_after`, `difficulty_after`, `due_at`,
  `retrievability_at_review`, `scheduler_version`).
- `concept_scheduler_state` — derived snapshot for fast dashboard reads; rebuildable from the log.
- `review_event_answer_payload` — quarantined, encrypted, tombstone-able free-text answers.

## 4. Pricing — gate design
Pricing is *gate design* around the habit, not a money conversation. The free/paid line is drawn by
**generation cost**, not by format. Cloze + self-graded recall cost zero AI and are unlimited in
Free. Metered (recurring monthly taster): **activations** and **AI-graded answers** only.

- **Free taster (hidden until hit):** 15 AI activations + 10 AI-graded answers / month.
- **Pro:** $9/mo or $79/yr (one flat tier).
- **Pro fair-use (internal, unpublished):** ~500 activations + ~300 AI-graded answers / month.
- **Abuse caps:** first activation once/concept lifetime; regen 1/concept/30d; ~50 activations/day.
- **Margin lever:** model tiering (expensive model only for the Distractor).
- *Numbers are starting values — validate against the spike's real $/activation.*

## 5. Gamification surface
- **Spine — topic-as-organism.** Health = honest rolled-up FSRS retrievability. Five mood tiers:
  thriving → healthy → fading → wilting → dormant. Plus a sixth **Unseeded (seed)** state for
  candidates with no review yet. Mascot is optional, stateless flavor.
- **Daily Drop (Free, cloze + self-grade):** near-cliff items first (~80–90% window), then top-up
  with fresh candidates constrained to gardens you're already in; cold-start exception pulls
  vault-wide (capped 1–2 new gardens/Drop) until ~3 topics seeded.
- **Boss Battles:** offered (never auto-fired) when 2+ related topics are fading/wilting; manual
  launch always; AI version is Pro.
- **Heatmap:** counts reviews that moved retrievability up, colored by distinct topics watered, not
  volume. No streak-break punishment.

## 6. Dashboard & coined metrics
Every number must be honest, actionable, on-mission. Three questions → three metrics:
- **Living Knowledge** (primary): share of seeded concepts currently retrievable. Companion label:
  "Coverage X% seeded" (Frontier inverted).
- **Net Recall + Next Best Action:** weekly restored − decayed; one deterministic rules-based CTA.
- **The Frontier** (secondary, always visible): imported-but-unseeded concepts, framed as
  invitation ("812 seeds waiting" + one-tap "Plant 5").
Dashboard has two modes: cold-start (Frontier hero) and steady-state (Living Knowledge hero); flips
event-driven on the first seed.

## 7. Quiz-surface laws
1. Retrieval is sacred — always reveal-gate the answer.
2. Color always carries health state (wilting never renders brand pink).
3. Commerce stays subordinate to the loop.

## 8. Edge & empty states (resolved — each falls out of a locked decision)
- **Taster wall mid-quiz:** never interrupt; silent fallback to free rails; pre-warning pill; calm
  info-styled notice at session boundary.
- **Vault still syncing:** seed-bed skeletons + non-blocking progress; never gated on 100%.
- **Cold-start first Drop:** framed as first-touch; cold-start exception feeds it.
- **Garden of one:** visible at ≥1 candidate; renders honestly as mostly-unplanted.
- **Contested AI grade:** contestable (tie → user, no punitive decay); shows reasoning; flag +
  preserved answer = grader-improvement data.

## 9. Tech stack
Postgres + pgvector · Java 21 / Spring Boot (modular monolith) · LangChain4j (no Python service for
V1) · Claude tiered (Haiku / Sonnet) · hosted embeddings · FSRS in Java behind `retrievability()` ·
Next.js/React/TS/Tailwind (#FE9AB7) · PWA web push for the Daily Drop (native RN/Expo fast-follow) ·
Notion REST + Obsidian folder ingest behind the Source Adapter · managed infra · Postgres-as-queue.
Java↔TS type seam closed by generating the TS client from the backend OpenAPI spec.

## 10. Build sequence (dependency-ordered)
| Seq | Task | Phase | Pri | Depends |
|----|------|-------|-----|---------|
| 1 | De-risking spike — prove/kill cost + grading quality | 0 De-risk | P0 | — |
| 2 | Postgres schema — event log + projections | 1 Core | P0 | 1 |
| 3 | Source Adapter + folder/markdown ingest | 1 Core | P0 | 1 |
| 4 | Lightweight candidate extraction | 1 Core | P0 | 3 |
| 5 | FSRS engine behind retrievability() interface | 1 Core | P0 | 2 |
| 6 | Self-graded review surface (cloze) | 1 Core | P0 | 4,5 |
| 7 | Living dashboard (cold-start + steady modes) | 1 Core | P0 | 5,6 |
| 8 | Deep activation pipeline (candidate → card) | 2 AI | P1 | 4,1 |
| 9 | MCQ format + AI grading (contestable) | 2 AI | P1 | 8 |
| 10 | Notion source adapter (hosted OAuth sync) | 2 AI | P1 | 3 |
| 11 | Metering + pricing gate (taster, fair-use, caps) | 3 Gate | P1 | 8,9 |
| 12 | Soft-wall + pre-warning + boundary upsell | 3 Gate | P1 | 11,6 |
| 13 | Mood-tier gardens (5 tiers + seed state) | 4 Game | P2 | 7 |
| 14 | Daily Drop (near-cliff + top-up + cold-start) | 4 Game | P2 | 7,13 |
| 15 | Heatmap (distinct-topics-watered) | 4 Game | P2 | 7 |
| 16 | Boss Battles (offered, not forced) | 4 Game | P2 | 13,9 |
| 17 | Net Recall + Frontier + Next Best Action | 4 Game | P2 | 7 |

**ENG-1 is the gate. Nothing in Phase 1+ is trustworthy until the spike reports cost + quality.**
