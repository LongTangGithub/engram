package com.engram.dashboard;

/**
 * Five health tiers for a seeded concept, derived from live FSRS retrievability.
 * A sixth state — UNSEEDED (seed) — is represented by a null MoodTier, not by this enum.
 * Null means "no review yet"; DORMANT means "reviewed but nearly forgotten".
 *
 * Thresholds are authoritative in {@link TierThresholds}. Color tokens that mirror these
 * names live in frontend/lib/tier-colors.ts — keep names in sync.
 */
public enum MoodTier {
    THRIVING,   // R >= 0.9
    HEALTHY,    // R >= 0.7
    FADING,     // R >= 0.5
    WILTING,    // R >= 0.3
    DORMANT     // R <  0.3
}
