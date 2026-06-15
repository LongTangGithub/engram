package com.engram.dashboard;

/**
 * Single source of truth for retrievability → MoodTier mapping.
 *
 * Thresholds (version: ENG-7, 2026-06-14):
 *   THRIVING  R >= 0.9   — strongly retained
 *   HEALTHY   R >= 0.7   — well retained
 *   FADING    R >= 0.5   — partially retained, review soon
 *   WILTING   R >= 0.3   — largely forgotten, needs review
 *   DORMANT   R <  0.3   — nearly forgotten
 *
 * RETRIEVABLE_THRESHOLD (0.7) defines the Living Knowledge numerator:
 * a concept is "retrievable" if it is HEALTHY or THRIVING. Fading/wilting/dormant
 * concepts are seeded but not counted as currently living knowledge.
 *
 * Do NOT inline these constants elsewhere. Always import from here.
 */
public final class TierThresholds {

    public static final double THRIVING_MIN    = 0.9;
    public static final double HEALTHY_MIN     = 0.7;
    public static final double FADING_MIN      = 0.5;
    public static final double WILTING_MIN     = 0.3;

    /** Threshold used for the Living Knowledge numerator. */
    public static final double RETRIEVABLE_THRESHOLD = HEALTHY_MIN;

    private TierThresholds() {}

    public static MoodTier classify(double retrievability) {
        if (retrievability >= THRIVING_MIN) return MoodTier.THRIVING;
        if (retrievability >= HEALTHY_MIN)  return MoodTier.HEALTHY;
        if (retrievability >= FADING_MIN)   return MoodTier.FADING;
        if (retrievability >= WILTING_MIN)  return MoodTier.WILTING;
        return MoodTier.DORMANT;
    }
}
