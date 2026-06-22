# Progress

Single source of truth for what's built, what's in flight, and what's next. Read this at the start of every task before touching code, and update it as you work — not in a batch at the end.

This file complements [`learnings.md`](./learnings.md) (rules from past mistakes) and the visible task list (§7 in `CLAUDE.md`). Think of it as the long-lived map; the task list is the short-lived "where am I right now."

---

## How to use this file

When starting a task:

1. Read the **Current Focus** and **In Progress** sections to recover context.
2. Check **Blocked / Open Questions** for anything that might affect what you're about to do.
3. Verify the most recent **Completed** entries match reality — if they don't, fix the file before continuing.

When working:

- Move items between sections as status changes. Don't leave stale entries.
- Every entry gets a date (`YYYY-MM-DD`) and a one-line summary. Link to PRs, issues, or files where useful.
- If you discover work mid-task, add it to **Backlog** or **In Progress** — don't silently expand scope.

When finishing:

- Move the item to **Completed** with the date and a short note on what was verified (tests passed, build green, deployed, etc.).
- If the work surfaced a lesson, append it to `learnings.md` and reference it here.

---

## Current Focus

**ENG-9 COMPLETE (MCQ auto-grading).** Click-to-grade MCQ wired end-to-end: server-side correctness (`POST /api/review/submit-mcq`), correct→Good(3)/wrong→Again(1) mapping, `grading_scheme_version="mcq-auto-v1"`, idempotency-coupled write, pick-to-grade UI with self-grade fallback preserved. AI free-text grading is split out to its own Backlog task (NOT built here). Next: ENG-10 (Notion source adapter) or ENG-11 (metering + pricing gate).

---

## In Progress

Work currently underway. One entry per concrete unit of work (feature, file, migration, etc.). Only items actively being worked on belong here.

| Date Started | Item | Owner / Branch | Status Notes |
|--------------|------|----------------|--------------|
| *(empty)* |  |  |  |

---

## Completed

Most recent at the top. Trim aggressively — anything older than the current milestone can be archived to `progress-archive.md` or deleted.

### 2026-06-22

- **ENG-9: MCQ auto-grading (click-to-grade)** — COMPLETE (code + Docker-free verification; DB-backed tests written, pending a Docker host to execute). Scope: MCQ click-to-grade ONLY — AI free-text grading is split out to its own Backlog task. Backend: `ReviewService.submitMcqReview()` loads the activated card, compares the picked option to the stored `correctAnswer` SERVER-SIDE (case/whitespace-normalized exact string match), maps correct→Good(3) / wrong→Again(1) (never Easy — recognition has scaffolding), records the event with `is_correct` = raw outcome (truth), `fsrs_rating` = derived interpretation, `format="mcq"`, `grading_scheme_version="mcq-auto-v1"`, `expected_answer_ref=cardId`. Reuses the ENG-2 append-then-apply idempotency-coupling rule (extracted a shared private `record(...)` path; self-grade and auto-grade both call it). `POST /api/review/submit-mcq` returns `{retrievabilityNow, dueAt, lifecycleState, isCorrect, correctAnswer}` — correct answer revealed ONLY post-commit. `POST /api/review/submit` (self-grade) kept for cloze + MCQ fallback. Frontend: `AiReviewCard` now pick-to-grade primary (click an option → commit → server grades → correct option green, wrong pick red, advance); self-grade reveal→grade flow preserved behind `selfGradeFallback` for when auto-grade fails. `lib/api.ts` gains `submitMcqReview` + types (defined locally — OpenAPI regen needs a running backend+Postgres, see frontend/CLAUDE.md). **Verified:** backend `compileJava`+`compileTestJava` green; new no-DB Law-1 DTO test passes (2/2 — pre-commit `/activate` + `/review/next` payloads carry options but no `correctAnswer`/correctness flag); frontend `tsc --noEmit` clean, Jest 38/38 (7 new pick-to-grade tests), `next build` green. **NOT verified here (no Docker in this container):** the 5 new DB-backed MCQ grading tests in `ReviewServiceTest` (correct→3, wrong→1, server-side grading without client answer, idempotency, no-card-throws) — they compile but need the pgvector Testcontainer; run in CI/local with Docker. Manual end-to-end (activate → wrong pick → right pick) also blocked on Docker/Postgres. Branch: `ENG-09/mcq-auto-grading`. See learnings.md for server-side-grading / option-id-vs-text / rating-mapping rules.

### 2026-06-21

- **ENG-8b: Activation Wiring + Frontend** — COMPLETE. 97/97 backend tests pass (3 new: A1 truly-cold-vault, A2 repair-then-retry, A2 persistent-failure). 31/31 frontend tests pass (11 new AiReviewCard Law-1 tests). TypeScript clean, `pnpm build` green. Part A (follow-ups to 8a): `Distractor.PROMPT_VERSION = "distractor-v1"`; `generation_prompt_version` now stores `"professor-v1/distractor-v1"`; `GenerationOrchestrator` does single bounded repair on answer-colliding distractors with retry cost accumulation; typed `ActivationGenerationException`. Part B (backend REST): `ActivationController` — `POST /api/activate` (lazy generate-once, returns shuffled options, NO correctAnswer pre-reveal) + `GET /api/activate/{conceptId}/reveal` (reveal-gated answer fetch). `GET /api/review/next` extended to return `NextCardResponse` with `cardType: cloze|mcq`; AI card served when `activated_card` exists (READ-ONLY, no generation). `SubmitRequest` now carries `format` field for event log provenance. Part C (frontend): `AiReviewCard.tsx` (Law-1 MCQ: all 4 options visible pre-reveal, no correct indicator, grade buttons only after Reveal + parent sets correctAnswer); review page handles mcq and cloze paths, lazy auto-activation with "Writing your question…" loading state, cloze fallback on activation failure (C4). `lib/api.ts` updated with new types + `activateCard`/`revealCard` helpers. Note: `api-types.ts` not yet regenerated (needs backend+Postgres running — see frontend/CLAUDE.md); types defined locally in `lib/api.ts` until regen. Branch: `ENG-8b-activation-wiring`.

- **ENG-8a: AI Activation Core** — COMPLETE. 94/94 tests pass (10 new + 84 prior). V4 Flyway migration: `activated_card` table (generate-once via UNIQUE on `concept_id` + `idempotency_key`) + `activated_at` column on `concept_candidate` (orthogonal to `lifecycle_state`). `com.engram.activation` package: `ClaudeClient` interface + `AnthropicClaudeClient` (direct HTTP, no LangChain4j), `ModelTier` (CHEAP=Haiku/Professor, EXPENSIVE=Sonnet/Distractor only), `Professor` (RAG-grounded in concept's `sourceSpan`), `Distractor` (vault-sourced via kNN neighbors; cold vault = sourceSpan fallback), `GenerationOrchestrator` (fixed pipeline, cost aggregation), `ActivatedCard` record (full provenance: tokens, cost_micros, model, prompt version), `ActivatedCardRepository` (JSONB distractors), `ActivationService` (generate-once cache gate — second call returns cached, zero LLM calls). `FakeClaudeClient` captures prompts for assertion. Red-first proof: `generateOnce` test shows `expected: <0> but was: <2>` without cache gate; green after restore. Wired in `EngramConfig` (plain beans, no scanBasePackages change). Branch: `ENG-8a-activation-core`.

- **Embedding pipeline (ENG-8 prereq)** — COMPLETE. All 84 tests pass (0 failures). V3 Flyway migration adds `vector(1536)` column + HNSW index (`m=16, ef_construction=200`). `EmbeddingProvider` interface (`embed`, `embedAll`, `dimension`, `modelId`). `FakeEmbeddingProvider` (deterministic hash → L2-normalized pseudo-vector, `callCount()` for zero-embed assertions). `OpenAiEmbeddingProvider` (direct HTTP, batch `embedAll`, text-embedding-3-small, 1536 dims). `CandidateIngestionService` now embeds ADDED and CHANGED docs only — UNCHANGED never touches the embedder (zero-embed test passes). `CandidateVectorRepository`: `findNearestNeighbors(userId, conceptId, k)` (cosine kNN, user-scoped, excludes self) + `backfill(userId, provider)` (idempotent). Wired in `EngramConfig`. Test infra: Testcontainers `pgvector/pgvector:pg16` with Flyway clean+migrate in `@BeforeEach`. No external dependency. Branch: `embedding-pipeline`. See learnings.md for Docker Desktop / TC 1.20.6 socket fix notes.

### 2026-06-14

- **ENG-7: Living dashboard (cold-start + steady-state modes)** — COMPLETE. Phase 1 COMPLETE. 75/75 backend tests pass. 21/21 frontend tests pass (8 ReviewCard + 13 Dashboard). `pnpm build` green (routes /, /review, /dashboard). Backend: `MoodTier` enum, `TierThresholds` (single source of truth for all 5 tier boundaries + RETRIEVABLE_THRESHOLD=0.7), `DashboardMode`, `ConceptView`/`GardenView`/`DashboardView` records, `DashboardRepository` (LEFT JOIN concept_candidate + concept_scheduler_state), `DashboardService` (live FSRS retrievability, null tier for unseeded, garden rollup), `DashboardController` (`GET /api/dashboard?userId=`). Frontend: regenerated `api-types.ts` (openapi-typescript v7), `lib/tier-colors.ts` (ONE token map — Law 2), `GardenCard.tsx`, `DashboardPage.tsx` (both modes), `/dashboard/page.tsx`. API smoke-tested: cold-start returns `COLD_START` mode with empty gardens; after seeding via review loop returns `STEADY_STATE` with THRIVING garden (retrievability≈0.998). Law-2 color tests enforce pink never appears on health tiers. See learnings.md for openapi-typescript v7 gotchas.

- **ENG-6 follow-up: ClozeGenerator + due_at fixes** — COMPLETE (2026-06-14). 51/51 backend tests pass. (1) ClozeGenerator now uses whole-word lookarounds `(?<![\\w-])...(?![\\w-])` — hyphens treated as word-connectors; Pattern.quote guards regex-special titles. Fallback uses `\b` boundaries. 4 new tests (2 confirmed failures before fix). (2) due_at computed at second precision: `reviewedAt.plusSeconds(round(stability * 86400))` — eliminates whole-day rounding drift. See learnings.md for the masking gotcha.

- **ENG-6: Self-graded review surface (full vertical slice)** — COMPLETE. 47/47 backend tests pass. 8/8 frontend Law-1 tests pass. `pnpm build` green (routes /, /review, /_not-found). Backend on port 8081 (8080 taken by Docker Desktop). Smoke test verified: `GET /api/review/next` returns cloze card, `POST /api/review/submit` returns `{retrievabilityNow, dueAt, lifecycleState: "SEEDED"}`. New: `ClozeGenerator`, `ReviewService` (nextCard + submitReview with FSRS, idempotency rule, CANDIDATE→SEEDED flip), `ReviewController` (`/api/review/next` + `/api/review/submit`), CORS for localhost:3000. Frontend: Next.js 16 App Router, Tailwind 4 (CSS-first), `ReviewCard.tsx` (reveal-gate), `/review` page, Law-1 component tests. First running app, first frontend. See learnings.md for port-8081 and dual-main-class gotchas.

### 2026-06-13

- **ENG-5: FSRS-6 engine behind RetrievabilityEngine interface** — COMPLETE. 15/15 tests pass (pure unit, no DB). Oracle: py-fsrs 6.3.1 (FSRS-6). Ported: initial S/D formulas, next recall stability, next forget stability, next difficulty (mean-reversion), retrievability formula. Oracle-match tolerance 1e-6 on all formula vectors. `scheduler_version` = "FSRS-6" (feed this into the `scheduler_version` column at ENG-6). Package: `com.engram.scheduler`. NOT wired to DB or event log — ENG-6's job.

- **ENG-4: Candidate extraction + persistence + incremental sync** — COMPLETE. 10/10 tests pass (5 repo, 5 service). Core proof verified: `secondRun_unchangedVault_zeroLlmCalls` — second run on unchanged vault makes 0 Extractor calls (Mockito `verify(extractor, never()).extract(any())`). Flyway V2 migration (`concept_candidate`), `LifecycleState` enum, `ConceptCandidate` record, `Extractor` interface, `ClaudeExtractor` impl (wired, not called in tests), `ConceptCandidateRepository` (JdbcTemplate), `CandidateIngestionService` orchestrator, `IngestionSummary` return type. All ENG-2/3 tests still green.

- **ENG-3: Source Adapter + Obsidian folder ingest** — COMPLETE. 7/7 new tests pass (pure filesystem, @TempDir, no DB). Verified: scan nested .md files with correct sourceRef/title/content/lastModified; ignores non-.md + .obsidian/.git/.trash; hash stable for identical content, changes on 1-byte diff; SyncDiff classifies added/changed/unchanged/removed correctly; empty folder → empty list; missing folder → IllegalArgumentException. Package invariant documented in `com/engram/ingest/CLAUDE.md`. No DB dependency (persistence-agnostic by design).

### 2026-06-12

- **ENG-2: Postgres event log + projection** — COMPLETE. 5/5 tests pass (embedded-postgres, no Docker). Verified: append-only trigger rejects UPDATE/DELETE; idempotent insert on (user_id, client_event_id); replay == incremental across 3 events (fixed: test now reads actual `concept_scheduler_state` row via `projection.read()`, not double-replay — break-checked: no-op applyEvent causes only that test to fail); tombstone nulls ciphertext while review_event row survives. ENG-1 spike re-runs clean (no regression). Idempotency-coupling contract logged in learnings.md.
- **ENG-1: de-risking spike** — PASSED. Spike ran end-to-end on `sample-note.md`. Cost: $0.006/activation (~$3/mo at 500 activations, target <$9). Card quality: Q atomic + genuine recall, A faithful to note, distractors plausible-but-wrong. Build fixes: added `jackson-databind` dep, fixed Gradle 7 toolchain syntax, generated `gradlew` wrapper. Unblocks all Phase 1 work.

---

## Backlog

Planned but not started. Group by area (`apps/web`, `services/billing`, `infra`, etc.) so it's easy to scan. Order within each group reflects priority.

### backend/ (Phase 1 — unblocked 2026-06-12)

- ~~ENG-2: Postgres schema~~ — done 2026-06-12
- ~~ENG-3: Source Adapter + folder/markdown ingest~~ — done 2026-06-13
- ~~ENG-4: Lightweight candidate extraction~~ — done 2026-06-13
- ~~ENG-5: FSRS engine behind `retrievability()` interface~~ — done 2026-06-14
- ~~ENG-6: Self-graded review surface (cloze)~~ — done 2026-06-14

### apps/web (Phase 1 — unblocked 2026-06-12)

- ~~ENG-7: Living dashboard (cold-start + steady modes)~~ — done 2026-06-14

### backend/ (Phase 2)

- ~~ENG-8a: AI Activation Core~~ — done 2026-06-21
- ~~ENG-8b: Wire activation into review flow + frontend~~ — done 2026-06-21
- ~~ENG-8: Deep activation pipeline~~ — done (ENG-8a + ENG-8b)
- ~~ENG-9: MCQ format + auto-grading~~ — done 2026-06-22 (MCQ click-to-grade only)
- ENG-9b: AI free-text grading (free-recall / Feynman, contestable) — split out of ENG-9. Server-side LLM grader, `user_answer` quarantined payload (V1-spec §7), `expected_answer_ref` + `grader_prompt_version` + `model_id`, contestable grade (tie → user, no punitive decay — §8). Metered (AI-graded answers, §4).
- ENG-10: Notion source adapter (hosted OAuth sync)

### backend/ (Phase 3)

- ENG-11: Metering + pricing gate (taster, fair-use, caps)
- ENG-12: Soft-wall + pre-warning + boundary upsell

### apps/web (Phase 4)

- ENG-13: Mood-tier gardens (5 tiers + seed state)
- ENG-14: Daily Drop (near-cliff + top-up + cold-start)
- ENG-15: Heatmap (distinct-topics-watered)
- ENG-16: Boss Battles
- ENG-17: Net Recall + Frontier + Next Best Action

---

## Blocked / Open Questions

Anything waiting on a decision, an external dependency, or clarification. Each entry should name what's blocking it and who/what unblocks it.

| Date | Item | Blocked By | Notes |
|------|------|------------|-------|
| *(empty)* |  |  |  |

---

## Decisions Log

Significant technical or product decisions made during the project. Append-only — don't rewrite history, add a new entry if a decision is reversed.

### 2026-06-12 — JdbcTemplate over jOOQ for ENG-2

- **Context:** Spec said "jOOQ preferred or JdbcTemplate." jOOQ requires code generation from schema (DB-first) which adds build complexity and fights a not-yet-stable schema.
- **Decision:** JdbcTemplate. Explicit SQL, no ORM, no codegen. Satisfies the core requirement (no JPA, no mutable-entity model).
- **Alternatives considered:** jOOQ DSL without codegen (possible but loses type safety); jOOQ with codegen (right call when schema stabilizes, likely after ENG-5).
- **Consequences:** Can migrate to jOOQ codegen when schema is stable. RowMapper boilerplate stays in repo classes for now.

### 2026-06-21 — ENG-8a: ClaudeClient as interface (not concrete class)

- **Context:** Tests need to fake LLM calls to assert on prompts passed in and count call volume.
- **Decision:** `ClaudeClient` is an interface; `AnthropicClaudeClient` is the impl. `FakeClaudeClient` in test package captures every `(tier, system, userText)` call.
- **Alternatives considered:** Mockito mock of concrete class (works but hides what prompts are sent); CostLog @Component (promoted from spike — swallows usage into side-channel, can't assert totals).
- **Consequences:** Generate-once test can assert call count = 0 on second activate. RAG-grounding and vault-sourced tests assert on prompt content, not outputs.

### 2026-06-21 — ENG-8a: activated_at ORTHOGONAL to lifecycle_state

- **Context:** Spec locks that ACTIVATED and SEEDED are independent axes.
- **Decision:** `activated_at TIMESTAMPTZ` added to `concept_candidate` (nullable); `lifecycle_state` is NOT modified by `ActivationService.activate()`. A concept can be SEEDED before activated (reviewed as cloze first) or CANDIDATE after (activated but never reviewed).
- **Consequences:** `ReviewService.flipToSeeded()` is unaffected. No ordering dependency between activation and review flows.

### 2026-06-21 — Testcontainers with pgvector/pgvector:pg16 (supersedes shared-DB approach)

- **Context:** V3 migration adds pgvector. `embedded-postgres` can't run it. Initial attempt at shared `engram_test` DB (no isolation, manual setup required) was rejected. Three interlocking Docker Desktop / TC issues blocked Testcontainers until now.
- **Decision:** Testcontainers `pgvector/pgvector:pg16`, single container per JVM, Flyway clean+migrate in `@BeforeEach`. Full isolation, no external dependency.
- **Fix:** (1) `docker.raw.sock` as `docker.host` in `~/.testcontainers.properties`; (2) `systemProperty("api.version", "1.44")` to override docker-java's 1.32 default; (3) `environment("TESTCONTAINERS_RYUK_DISABLED", "true")` — ryuk bind-mount fails on docker.raw.sock; (4) `resolutionStrategy.eachDependency` to force TC 1.20.6 past Spring Boot BOM's 1.19.8 pin.
- **Consequences:** CI needs Docker. No manual DB creation step.

### 2026-06-22 — ENG-9: exact-string option match over option-IDs (V1)

- **Context:** MCQ auto-grade needs to know which option the user picked without shipping the correct answer to the client pre-commit (Law 1). Two options: (a) return options as `{optionId, text}` with a server-side correct-id mapping; (b) return bare option strings and have the client send back the picked string for server comparison.
- **Decision:** (b) exact-string match, case/whitespace-normalized, against the stored `correct_answer`. The existing `/activate` + `/review/next` DTOs already return `List<String>`; option-IDs would change the wire shape, the frontend, and the ENG-8b DTO tests for no V1 grading benefit.
- **Why it's still Law-1-safe:** the client already HAS all 4 option strings (it rendered them) but is told nothing about which is correct; correctness is decided server-side from the stored card. Sending the picked text back on commit leaks nothing (the user already committed).
- **Consequences / seam:** model-generated options are assumed distinct (orchestrator already dedupes). If options ever collide after normalization, grading is ambiguous — that's the trigger to move to stable option-IDs. Documented in learnings.md.

### 2026-06-22 — ENG-9: MCQ rating mapping correct→Good(3) / wrong→Again(1), never Easy

- **Context:** Recognition (MCQ) gives the answer among the options — scaffolding that pure recall doesn't. Mapping a correct MCQ to Easy(4) would inflate FSRS stability and under-schedule the concept.
- **Decision:** correct→Good(3), wrong→Again(1). Hard(2)/Easy(4) are not emitted by the auto-grader. Response-latency-based refinement (e.g. fast-correct → Easy) is a deliberate future seam, not built in ENG-9.
- **Consequences:** `is_correct` (raw outcome) is the truth; `fsrs_rating` is the versioned interpretation under `grading_scheme_version="mcq-auto-v1"`. A future scheme version can re-map without rewriting history (replay reads raw outcome, never the stored rating).

### 2026-06-12 — embedded-postgres over Testcontainers for integration tests

- **Context:** Testcontainers' docker-java client gets a stub 400 from Docker Desktop's gateway socket on this machine even though `docker run` works via the CLI. Root cause: docker-java negotiates differently than the Docker CLI.
- **Decision:** `io.zonky.test:embedded-postgres`. Real Postgres binary, no Docker dependency, ~1s startup.
- **Superseded:** 2026-06-21 — pgvector requirement forced switch to `engram_test` in dev container. embedded-postgres removed.

---

## Milestones

High-level checkpoints. Update when a milestone ships.

- [ ] **M1 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M2 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M3 — &lt;name&gt;** — target: YYYY-MM-DD