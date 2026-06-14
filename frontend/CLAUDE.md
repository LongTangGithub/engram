# frontend — Claude Code context

## API client

`lib/api-types.ts` is GENERATED from the backend OpenAPI spec — never hand-edit it.

To regenerate after backend API changes:
1. Start the backend (`./gradlew bootRun` with Postgres running).
2. Run: `npx openapi-typescript http://localhost:8081/v3/api-docs -o lib/api-types.ts`
3. Commit the updated file alongside the backend change.

`lib/api.ts` wraps the generated types in typed fetch helpers — this IS hand-edited.

## Law 1 — reveal gate (non-negotiable)

The answer to a cloze card must NEVER appear in the DOM before the user clicks Reveal.
- Never render the answer string alongside the un-answered prompt.
- Grade buttons (Again / Hard / Good / Easy) must appear only after Reveal is clicked.
- Tests enforce this: see `__tests__/ReviewCard.test.tsx`.

## Tier color invariant (Law 2)

Health colors live in **ONE place**: `lib/tier-colors.ts` → `TIER_COLORS` map + `tierColorClass()`.
CSS classes are in `app/globals.css` (`.tier-thriving`, `.tier-healthy`, etc.).

**NEVER assign `tier-seed` (brand pink) to a health tier.** Pink is for:
- Unseeded/seed state (null `moodTier`)
- Brand/positive accent (CTA buttons, the Frontier label)

Thresholds that generate each tier come from the backend's `TierThresholds.java` — do not redefine them in the frontend. The frontend only maps tier NAME → color class.

| Tier      | Class           | Color     | When                   |
|-----------|-----------------|-----------|------------------------|
| THRIVING  | tier-thriving   | green     | R >= 0.9               |
| HEALTHY   | tier-healthy    | emerald   | R >= 0.7               |
| FADING    | tier-fading     | yellow    | R >= 0.5               |
| WILTING   | tier-wilting    | orange    | R >= 0.3               |
| DORMANT   | tier-dormant    | gray      | R < 0.3                |
| (null)    | tier-seed       | **pink**  | Unseeded — seed state  |

## Brand tokens

| Token   | Value     |
|---------|-----------|
| bg      | #ffffff   |
| ink     | #222222   |
| accent  | #FE9AB7   |
| font    | Space Grotesk |
