# ENG-1 — the de-risking spike

This is the **gate** for the whole build. Its only job: return two facts before any production
code gets written.

1. **Real cost per activation** — the dollar spend of one Professor + Distractor run.
2. **Real generation quality** — are the distractors plausible-but-wrong, and is the question
   genuine recall, on a real note?

If either fails, fix the assumption *before* building Phase 1. Do not build the dashboard, event
log, sync, or UI first. (See the root `CLAUDE.md`.)

## What it does
Runs the core loop on one note:
`ingest → candidate extraction (cheap) → activation: Professor (cheap) + Distractor (expensive)
→ write a review event → read it back through FSRS`, then prints a cost report and the generated card.

## Setup (two required steps)
1. **Set current prices.** Open `src/main/java/com/engram/spike/llm/ModelTier.java` and set
   `inputPricePerMTok` / `outputPricePerMTok` for both tiers to the **current published** Anthropic
   prices (anthropic.com/pricing). They ship as `0.0` on purpose — the spike refuses to report a
   fake cost and will warn until you set them. Confirm the model IDs too.
2. **Export your key:** `export ANTHROPIC_API_KEY=sk-...` (never commit it).

## Run
```bash
# from backend/
gradle wrapper            # one-time, if you don't already have ./gradlew
./gradlew bootRun --args="sample-note.md"
# or point at your own note:
./gradlew bootRun --args="/path/to/your/note.md"
```
(Requires JDK 21. If `gradle` isn't installed, use your IDE's Gradle import or install Gradle 8+.)

## Read the output
- **GENERATED CARD** block → eyeball quality. Good = distractors are tempting but wrong, the
  question demands recall, the answer is faithful to the note. Bad = distractors obviously off, or
  the question is shallow trivia.
- **ACTIVATION COST REPORT** → the headline number, `TOTAL / activation`.

## Decide
- Cost: is `cost/activation` sane against a $9/mo Pro sub at ~500 activations/mo fair-use?
  (Rough sniff test: 500 × cost should be a small fraction of $9 for a typical user.)
- Quality: good enough that AI MCQ + grading is a real upgrade worth paying for?
- If the Distractor underwhelms, that's expected partly because true *vault-sourced* distractors
  (neighbors from the user's own vault via pgvector) don't exist yet — that's ENG-4/ENG-8. Judge
  whether the gap looks closable.

## Deliberate scope limits (don't "fix" these in the spike)
- No DB — the review event prints to stdout. The append-only schema is ENG-2.
- No pgvector / RAG — grounding is the raw note. Vault-sourced distractors are ENG-8.
- `FsrsReadback` is a tiny stand-in — the real FSRS engine behind `retrievability()` is ENG-5.
- Direct HTTP `ClaudeClient`, not LangChain4j — on purpose, to expose token usage. LangChain4j
  arrives at ENG-8.
