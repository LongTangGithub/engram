package com.engram.scheduler;

import java.time.Instant;

/**
 * Swappable seam for the memory-decay engine.
 * Default impl: FSRS-6. Layer-2 semantic priors are the earmarked first replacement.
 */
public interface RetrievabilityEngine {

    /**
     * Predicted recall probability at {@code now}, in [0, 1].
     * Returns 0 if the concept has never been reviewed.
     */
    double retrievability(FsrsState state, Instant now);

    /**
     * State transition after a single review.
     *
     * @param prior      the state before this review; null means this is the first review
     * @param rating     1=Again 2=Hard 3=Good 4=Easy
     * @param reviewedAt when the review occurred
     */
    FsrsState review(FsrsState prior, int rating, Instant reviewedAt);

    /** Algorithm identifier written into scheduler_version on every review event. */
    String version();
}