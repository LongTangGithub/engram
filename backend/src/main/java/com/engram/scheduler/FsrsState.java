package com.engram.scheduler;

import java.time.Instant;

/**
 * Minimal FSRS scheduler state carried per concept per user.
 * Mapping to/from DB columns (stability_after, difficulty_after, due_at) is ENG-6's job.
 */
public record FsrsState(
        double stability,
        double difficulty,
        Instant lastReviewedAt
) {}