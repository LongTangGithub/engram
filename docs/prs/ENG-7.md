# ENG-7: Living Dashboard — Cold-Start + Steady-State Modes

## Description

- Adds a living dashboard at `/dashboard` that shows two distinct modes: **COLD_START** (no seeded concepts yet) and **STEADY_STATE** (≥1 seeded concept), driven by a new `GET /api/dashboard?userId=` endpoint
- Health of each concept is computed live using the FSRS retrievability formula — no cached tier stored in DB
- Five mood tiers (THRIVING / HEALTHY / FADING / WILTING / DORMANT) plus a null "seed state" for unseeded concepts, with thresholds owned exclusively by `TierThresholds.java`

## Changes

- **`com.engram.dashboard`** — new package: `MoodTier`, `TierThresholds`, `DashboardMode`, `ConceptView`, `GardenView`, `DashboardView`, `DashboardRepository`, `DashboardService`, `DashboardController`
- **`EngramConfig`** — wired `DashboardRepository` + `DashboardService` beans; CORS updated to allow `localhost:3001`
- **`EngramApplication`** — added `com.engram.dashboard` to `scanBasePackages`
- **`frontend/lib/tier-colors.ts`** — single token map for tier → CSS class (Law 2: `tier-seed` pink is never assigned to a health tier)
- **`frontend/components/GardenCard.tsx`** — color-coded garden card with singular/plural copy and redundant avg retention hidden for single-concept gardens
- **`frontend/components/DashboardPage.tsx`** — both modes; cold-start shows frontier count + CTA; steady-state shows Living Knowledge % + "Review now" link with distinct styling (dark text, pink underline)
- **`frontend/app/dashboard/page.tsx`** — client route, fetches via `fetchDashboard`
- **`frontend/app/layout.tsx`** — background `#F8F8F8` so cards have visual lift
- **`frontend/lib/api-types.ts`** — regenerated from OpenAPI (v7 format: `components["schemas"]`)

## Testing

- **Before:** no dashboard existed; `/dashboard` returned 404
- **After:** cold-start mode renders frontier count + CTA; steady-state renders Living Knowledge %, per-garden tier color cards (green = THRIVING, gray = DORMANT, pink = unseeded only)
- 75/75 backend tests pass (`TierThresholdsTest` covers all 5 boundary edges; `DashboardServiceTest` covers mode flip, null tier for unseeded, all 5 health tiers, Living Knowledge math, garden grouping)
- 21/21 frontend tests pass (13 Dashboard tests covering both modes + Law-2 color invariant)

## Screenshots

![Dashboard steady-state](../frontend/Screenshots/Screenshot%202026-06-14%20at%207.08.45%20PM.png)

## Additional Comments

- Law 2 invariant: `tier-seed` (brand pink `#FE9AB7`) is **never** applied to a health tier. Enforced in `tier-colors.ts`, documented in `frontend/CLAUDE.md`, and tested in `Dashboard.test.tsx`
- `RETRIEVABLE_THRESHOLD = 0.7` (= `HEALTHY_MIN`) — Living Knowledge numerator counts HEALTHY+ only
- Phase 1 (ENG-1 → ENG-7) is now complete

# Checklist

- [x] All tests are passing
- [x] Documentation updated (`docs/progress.md`, `docs/learnings.md`, `frontend/CLAUDE.md`)
- [x] No TODOs left
- [x] Code is formatted properly
