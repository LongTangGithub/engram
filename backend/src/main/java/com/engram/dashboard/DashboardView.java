package com.engram.dashboard;

import java.util.List;

/**
 * Top-level dashboard response.
 *
 * mode             COLD_START iff no seeded concepts exist; STEADY_STATE otherwise.
 * livingKnowledgePct  null in COLD_START; else (retrievableCount / seededCount) * 100.
 *                  A concept is "retrievable" when R >= TierThresholds.RETRIEVABLE_THRESHOLD (0.7).
 * frontierCount    unseeded candidates — framed as invitation ("N seeds waiting").
 * netRecall        OUT OF SCOPE (ENG-17); not computed here.
 */
public record DashboardView(
        DashboardMode    mode,
        Double           livingKnowledgePct,  // null in COLD_START
        int              seededCount,
        int              retrievableCount,
        int              frontierCount,
        List<GardenView> gardens
) {}
