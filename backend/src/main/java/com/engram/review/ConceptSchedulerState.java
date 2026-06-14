package com.engram.review;

import java.time.Instant;
import java.util.UUID;

/**
 * Derived projection of concept_scheduler_state — fast dashboard reads.
 * Rebuildable by replaying review_event in occurred_at, seq order.
 */
public record ConceptSchedulerState(
        UUID    conceptId,
        UUID    userId,
        Double  stability,
        Double  difficulty,
        Instant dueAt,
        Double  retrievabilityLast,
        String  schedulerVersion,
        UUID    lastEventId,
        Instant lastReviewedAt,
        int     reviewCount,
        Instant updatedAt
) {}