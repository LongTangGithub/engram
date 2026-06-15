package com.engram.dashboard;

import java.util.List;

/**
 * A topic garden — all concepts sharing a topic_tag.
 *
 * Rollup: avgRetrievability = mean retrievability of SEEDED concepts in this garden.
 * Null when no seeded concepts exist (all-unseeded garden renders in seed/brand state).
 * rolledUpTier = TierThresholds.classify(avgRetrievability), or null for all-unseeded.
 *
 * Simple average is intentional for ENG-7; weighted/smart rollups are deferred.
 */
public record GardenView(
        String           topicTag,
        int              totalCount,         // all concepts in this garden
        int              seededCount,        // concepts with lifecycleState == SEEDED
        int              frontierCount,      // unseeded (totalCount - seededCount)
        Double           avgRetrievability,  // null if no seeded concepts
        MoodTier         rolledUpTier,       // null if no seeded concepts
        List<ConceptView> concepts
) {}
