# Engram

**Cure the collector's fallacy.** Engram turns a static second brain — your Obsidian vault, your uploaded notes — into durable memory through AI-orchestrated spaced repetition.

Instead of hoarding notes you never revisit, Engram extracts concepts from them, generates MCQ flashcards via Claude, and quizzes you on a schedule tuned to how memory actually decays. Each topic appears as a living "garden" that grows when you review and fades when you neglect it.

---

## Status

Active development. Phase 2 complete — AI card generation wired into the review loop end-to-end.

| Phase | What | Status |
|-------|------|--------|
| Phase 1 | Cloze review loop, FSRS scheduling, living dashboard | ✅ Done |
| Phase 2 | AI card generation (MCQ) with vault-sourced distractors | ✅ Done |
| Phase 3 | Metering + pricing gates | 🔜 Planned |

---

## Stack

| Layer | Tech |
|-------|------|
| Backend | Java 21 · Spring Boot 3 · JdbcTemplate (no ORM) |
| Database | PostgreSQL 16 + pgvector (embeddings + kNN) |
| AI | Claude (Haiku for extraction, Sonnet for distractors) · OpenAI text-embedding-3-small |
| Scheduling | FSRS-6 (spaced repetition algorithm) |
| Frontend | Next.js 16 · React · TypeScript · Tailwind 4 |

---

## Prerequisites

- JDK 21
- Docker Desktop (for pgvector Postgres)
- Node.js 20+ and pnpm
- Anthropic API key (`sk-ant-...`)
- OpenAI API key (`sk-...`) — used for embeddings only

---

## Quick start

### 1. Start Postgres

```bash
docker run -d --name engram-postgres \
  -e POSTGRES_DB=engram \
  -e POSTGRES_USER=engram \
  -e POSTGRES_PASSWORD=engram \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

### 2. Set API keys

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
```

Or add both to `~/.zshrc` / `~/.bashrc` to persist.

### 3. Start the backend

```bash
cd backend
./gradlew bootRun
# → listens on http://localhost:8081
# Flyway runs migrations automatically on first boot
```

### 4. Start the frontend

```bash
cd frontend
pnpm install
pnpm dev
# → http://localhost:3000
```

### 5. Seed a concept (skip vault sync for now)

```bash
psql postgresql://engram:engram@localhost:5432/engram -c "
INSERT INTO concept_candidate
  (concept_id, user_id, source_type, source_ref, source_content_hash,
   title, topic_tag, source_span, lifecycle_state)
VALUES
  ('00000000-0000-0000-0000-000000000010',
   '00000000-0000-0000-0000-000000000001',
   'OBSIDIAN_FOLDER', 'test.md', 'hash1',
   'Spaced Repetition', 'memory',
   'Spaced repetition exploits the spacing effect to improve long-term retention.',
   'CANDIDATE')
ON CONFLICT DO NOTHING;"
```

### 6. Open the review page

Go to **http://localhost:3000/review** — the app will generate an AI flashcard on first visit (takes ~5-10s while Claude writes the question and distractors), then serve it instantly on every subsequent visit.

---

## Running tests

```bash
# Backend (97 tests — requires Docker for pgvector Testcontainers)
cd backend && ./gradlew test

# Frontend (31 tests)
cd frontend && pnpm test

# TypeScript check
cd frontend && pnpm exec tsc --noEmit
```

---

## Project layout

```
backend/      Java/Spring service
frontend/     Next.js app
docs/
  V1-spec.md  Full design spec — start here for the why
  progress.md Live project status
  learnings.md Accumulated gotchas and conventions
CLAUDE.md     Agent working rules (for Claude Code)
```

---

## Key API endpoints

| Method | Path | What |
|--------|------|------|
| `GET` | `/api/review/next?userId=` | Next card to review (`cardType: cloze\|mcq`) |
| `POST` | `/api/review/submit` | Submit a self-graded review |
| `POST` | `/api/activate` | Generate (or return cached) MCQ card for a concept |
| `GET` | `/api/activate/{conceptId}/reveal` | Fetch correct answer after user clicks Reveal |
| `GET` | `/api/dashboard?userId=` | Garden view with retrievability per topic |

---

## Design

Full spec in **`docs/V1-spec.md`**. Key decisions:

- **Generate-once:** MCQ cards are generated once per concept and cached. Second call returns the same card with zero LLM calls.
- **Law 1 (reveal gate):** The correct answer is never in the DOM before the user clicks Reveal. The `/activate` endpoint returns shuffled options with no correct flag; `/reveal` is fetched separately.
- **FSRS-6:** Due dates are computed from the real-valued stability interval, not whole-day rounding.
- **Vault-sourced distractors:** kNN neighbors from the user's own vault ground distractor generation, making wrong answers plausible to someone with shallow knowledge.
