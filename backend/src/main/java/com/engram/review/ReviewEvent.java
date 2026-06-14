package com.engram.review;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable review event — a fact that a review happened.
 *
 * Raw outcome (isCorrect, score, hintUsed) is the source of truth.
 * fsrsRating is a derived, versioned interpretation — never treat it as canonical on replay.
 * Scheduler fields are a snapshot carried through from the event; FSRS math is ENG-5.
 *
 * Supersedes com.engram.spike.review.ReviewEvent (ENG-2).
 */
public record ReviewEvent(
        // identity & ordering
        UUID    eventId,
        long    seq,
        String  clientEventId,
        UUID    userId,
        UUID    conceptId,
        Instant occurredAt,

        // session context
        UUID    sessionId,
        String  sessionType,
        String  format,
        Integer responseLatencyMs,

        // raw outcome (truth)
        boolean isCorrect,
        Double  score,
        boolean hintUsed,

        // derived grade (versioned interpretation)
        Integer fsrsRating,
        String  gradingSchemeVersion,
        String  expectedAnswerRef,
        String  graderPromptVersion,
        String  modelId,

        // scheduler snapshot
        Double  stabilityAfter,
        Double  difficultyAfter,
        Instant dueAt,
        Double  retrievabilityAtReview,
        String  schedulerVersion
) {}