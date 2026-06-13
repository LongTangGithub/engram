package com.engram.spike.review;

import java.time.Instant;

/**
 * A review event — an immutable FACT that a review happened.
 *
 * <p>This spike version carries the essential fields; the full append-only schema
 * (idempotency key, session context, quarantined answer payload, versioned grade,
 * scheduler snapshot) is built at ENG-2. The invariant is already in force here:
 * an event is never mutated, and the FSRS grade is a *derived* interpretation of
 * the raw outcome — not the source of truth.
 *
 * @param fsrsRating the 1-4 Again/Hard/Good/Easy grade (derived from the raw outcome)
 */
public record ReviewEvent(
        String eventId,
        String conceptTitle,
        Instant occurredAt,
        String format,        // e.g. "mcq" | "free_recall" | "cloze"
        boolean isCorrect,    // raw outcome (truth)
        int fsrsRating        // derived interpretation (1=Again .. 4=Easy)
) {}
