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

ENG-6 done. First running app + first frontend. Next: ENG-7 (living dashboard).

---

## In Progress

Work currently underway. One entry per concrete unit of work (feature, file, migration, etc.). Only items actively being worked on belong here.

| Date Started | Item | Owner / Branch | Status Notes |
|--------------|------|----------------|--------------|
| *(none)* | | | |

---

## Completed

Most recent at the top. Trim aggressively — anything older than the current milestone can be archived to `progress-archive.md` or deleted.

### 2026-06-14

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

- ENG-7: Living dashboard (cold-start + steady modes)

### backend/ (Phase 2)

- ENG-8: Deep activation pipeline (candidate → card, with RAG + vault-sourced distractors)
- ENG-9: MCQ format + AI grading (contestable)
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

### 2026-06-12 — embedded-postgres over Testcontainers for integration tests

- **Context:** Testcontainers' docker-java client gets a stub 400 from Docker Desktop's gateway socket on this machine even though `docker run` works via the CLI. Root cause: docker-java negotiates differently than the Docker CLI.
- **Decision:** `io.zonky.test:embedded-postgres`. Real Postgres binary, no Docker dependency, ~1s startup.
- **Alternatives considered:** Fix Docker Desktop socket (needs UI change by user); Colima (not installed); raw Postgres.app (not running).
- **Consequences:** No Docker needed for tests. CI must have embedded-postgres available (it bundles the binary, so it works on Linux/macOS without a separate install).

---

## Milestones

High-level checkpoints. Update when a milestone ships.

- [ ] **M1 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M2 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M3 — &lt;name&gt;** — target: YYYY-MM-DD