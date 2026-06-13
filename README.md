# Engram

**Cure the collector's fallacy.** Engram turns a static second brain — your Notion and
Obsidian vaults, your uploaded notes — into durable memory through AI-orchestrated,
gamified spaced repetition. Think "Readwise for active recall."

Instead of hoarding notes you never revisit, Engram extracts the concepts inside them,
quizzes you on a schedule tuned to how memory actually decays, and shows each topic as a
living "garden" whose health rises when you review and falls when you neglect it.

## Status

Early development. Building V1 for PKM power users. Current work is **ENG-1**, a de-risking
spike that validates AI cost and quality before the full build — see `backend/README.md`.

## Stack

- **Backend:** Java 21 · Spring Boot · PostgreSQL + pgvector
- **AI:** Claude (tiered) via LangChain4j · hosted embeddings · FSRS for scheduling
- **Frontend:** Next.js · React · TypeScript · Tailwind *(arrives in Phase 1)*

## Layout

| Path | What's there |
|------|--------------|
| `backend/` | Java/Spring service — the ENG-1 spike lives here |
| `docs/V1-spec.md` | Full design spec: decisions, data model, pricing, build sequence |
| `CLAUDE.md` | Agent working rules (for Claude Code) |
| `progress.md` | Live project status |

## Running the spike

Requires JDK 21 and an Anthropic API key.

```bash
cd backend
# set current model prices in src/main/java/com/engram/spike/llm/ModelTier.java first
export ANTHROPIC_API_KEY=sk-...
./gradlew bootRun --args="sample-note.md"
```

It runs the real pipeline on one note and prints the cost per activation plus the generated
card. See `backend/README.md` for what to measure.

## Docs

The design source of truth is **`docs/V1-spec.md`**. Start there to understand *why* the
system is built the way it is.