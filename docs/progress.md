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

ENG-1 de-risking spike: prove real cost/activation and generation quality before any Phase 1 code. Gate task — nothing downstream is trustworthy until this reports numbers.

---

## In Progress

Work currently underway. One entry per concrete unit of work (feature, file, migration, etc.). Only items actively being worked on belong here.

| Date Started | Item | Owner / Branch | Status Notes |
|--------------|------|----------------|--------------|
| 2026-06-12 | ENG-1: de-risking spike | master | Backend scaffold exists (`backend/`). Need to set real Anthropic prices in `ModelTier.java`, export API key, run `./gradlew bootRun --args="sample-note.md"`, eyeball card quality, record cost/activation. |

---

## Completed

Most recent at the top. Trim aggressively — anything older than the current milestone can be archived to `progress-archive.md` or deleted.

### YYYY-MM-DD

- *(no completed work yet)*

---

## Backlog

Planned but not started. Group by area (`apps/web`, `services/billing`, `infra`, etc.) so it's easy to scan. Order within each group reflects priority.

### backend/ (Phase 1 — blocked on ENG-1)

- ENG-2: Postgres schema — `review_event`, `concept_scheduler_state`, `review_event_answer_payload`
- ENG-3: Source Adapter + folder/markdown ingest → `IngestedDocument`
- ENG-4: Lightweight candidate extraction (cheap pass at import)
- ENG-5: FSRS engine behind `retrievability()` interface
- ENG-6: Self-graded review surface (cloze)

### apps/web (Phase 1 — blocked on ENG-1)

- ENG-7: Living dashboard (cold-start + steady modes)

### backend/ (Phase 2 — blocked on ENG-1)

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

### YYYY-MM-DD — &lt;decision title&gt;

- **Context:** what prompted the decision
- **Decision:** what was chosen
- **Alternatives considered:** what else was on the table
- **Consequences:** what this commits us to

---

## Milestones

High-level checkpoints. Update when a milestone ships.

- [ ] **M1 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M2 — &lt;name&gt;** — target: YYYY-MM-DD
- [ ] **M3 — &lt;name&gt;** — target: YYYY-MM-DD