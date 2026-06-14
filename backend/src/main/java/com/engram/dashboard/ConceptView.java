package com.engram.dashboard;

/**
 * Per-concept entry within a garden.
 * retrievability and moodTier are null for unseeded concepts — NOT zero/DORMANT.
 * Null means "no review recorded yet" (seed state); DORMANT means "reviewed but nearly forgotten".
 */
public record ConceptView(
        String    title,
        String    lifecycleState,
        Double    retrievability,  // null if unseeded
        MoodTier  moodTier         // null if unseeded
) {}
