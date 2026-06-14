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

## Brand tokens

| Token   | Value     |
|---------|-----------|
| bg      | #ffffff   |
| ink     | #222222   |
| accent  | #FE9AB7   |
| font    | Space Grotesk |
