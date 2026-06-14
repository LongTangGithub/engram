package com.engram.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boundary-value tests for TierThresholds.classify().
 * Tests every threshold edge; thresholds are inclusive at the minimum.
 */
class TierThresholdsTest {

    @Test void exactThriving()       { assertEquals(MoodTier.THRIVING, TierThresholds.classify(0.9)); }
    @Test void aboveThriving()       { assertEquals(MoodTier.THRIVING, TierThresholds.classify(1.0)); }
    @Test void justBelowThriving()   { assertEquals(MoodTier.HEALTHY,  TierThresholds.classify(0.8999)); }

    @Test void exactHealthy()        { assertEquals(MoodTier.HEALTHY,  TierThresholds.classify(0.7)); }
    @Test void justBelowHealthy()    { assertEquals(MoodTier.FADING,   TierThresholds.classify(0.6999)); }

    @Test void exactFading()         { assertEquals(MoodTier.FADING,   TierThresholds.classify(0.5)); }
    @Test void justBelowFading()     { assertEquals(MoodTier.WILTING,  TierThresholds.classify(0.4999)); }

    @Test void exactWilting()        { assertEquals(MoodTier.WILTING,  TierThresholds.classify(0.3)); }
    @Test void justBelowWilting()    { assertEquals(MoodTier.DORMANT,  TierThresholds.classify(0.2999)); }

    @Test void zero()                { assertEquals(MoodTier.DORMANT,  TierThresholds.classify(0.0)); }
    @Test void midThriving()         { assertEquals(MoodTier.THRIVING, TierThresholds.classify(0.95)); }
    @Test void midHealthy()          { assertEquals(MoodTier.HEALTHY,  TierThresholds.classify(0.8)); }
    @Test void midFading()           { assertEquals(MoodTier.FADING,   TierThresholds.classify(0.6)); }
    @Test void midWilting()          { assertEquals(MoodTier.WILTING,  TierThresholds.classify(0.4)); }
    @Test void midDormant()          { assertEquals(MoodTier.DORMANT,  TierThresholds.classify(0.15)); }
}
