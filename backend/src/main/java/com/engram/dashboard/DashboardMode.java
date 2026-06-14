package com.engram.dashboard;

/**
 * Dashboard rendering mode.
 * COLD_START  — no seeded concepts yet; Frontier is the hero.
 * STEADY_STATE — at least one seeded concept exists; Living Knowledge is the hero.
 * Flips event-driven on the first review (spec §6).
 */
public enum DashboardMode {
    COLD_START,
    STEADY_STATE
}
