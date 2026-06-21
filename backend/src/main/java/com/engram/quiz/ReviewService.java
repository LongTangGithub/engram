package com.engram.quiz;

import com.engram.concept.ConceptCandidateRepository;
import com.engram.review.ConceptSchedulerState;
import com.engram.review.ReviewEvent;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.FsrsState;
import com.engram.scheduler.RetrievabilityEngine;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class ReviewService {

    private final ConceptCandidateRepository ccRepo;
    private final ReviewEventRepository eventRepo;
    private final SchedulerProjection projection;
    private final RetrievabilityEngine engine;
    private final ClozeGenerator clozeGenerator;

    public ReviewService(ConceptCandidateRepository ccRepo,
                         ReviewEventRepository eventRepo,
                         SchedulerProjection projection,
                         RetrievabilityEngine engine,
                         ClozeGenerator clozeGenerator) {
        this.ccRepo = ccRepo;
        this.eventRepo = eventRepo;
        this.projection = projection;
        this.engine = engine;
        this.clozeGenerator = clozeGenerator;
    }

    /** Returns the next card to review for this user, or empty if no candidates exist. */
    public Optional<ClozeCard> nextCard(UUID userId) {
        return ccRepo.findNextDue(userId).map(clozeGenerator::generate);
    }

    /**
     * Records a self-graded review and advances the FSRS state.
     * format: "cloze" | "mcq" — stored in the immutable event log for provenance.
     *
     * @param rating 1=Again 2=Hard 3=Good 4=Easy
     */
    public ReviewResult submitReview(UUID userId, UUID conceptId, int rating,
                                     String clientEventId, Instant reviewedAt) {
        return submitReview(userId, conceptId, rating, clientEventId, reviewedAt, "cloze");
    }

    public ReviewResult submitReview(UUID userId, UUID conceptId, int rating,
                                     String clientEventId, Instant reviewedAt, String format) {
        // 1. Read prior FSRS state (null = first review)
        ConceptSchedulerState prior = projection.read(conceptId, userId);
        boolean isFirstReview = prior == null;

        FsrsState priorState = prior == null ? null
                : new FsrsState(prior.stability(), prior.difficulty(), prior.lastReviewedAt());

        // 2. Retrievability at the moment of this review (before updating state)
        double retrievabilityAtReview = priorState == null ? 0.0
                : engine.retrievability(priorState, reviewedAt);

        // 3. FSRS state transition
        FsrsState newState = engine.review(priorState, rating, reviewedAt);

        // 4. Due date: stability days at second precision (avoids rounding whole days → scheduling drift)
        Instant dueAt = reviewedAt.plusSeconds(Math.round(newState.stability() * 86400));

        // 5. Build review event
        ReviewEvent event = new ReviewEvent(
                UUID.randomUUID(),
                0L,                          // seq is DB-assigned (BIGSERIAL)
                clientEventId,
                userId,
                conceptId,
                reviewedAt,
                null, null,                  // sessionId, sessionType (ENG-6 scope: unused)
                format,
                null,                        // responseLatencyMs
                rating > 1,                  // isCorrect: Again=wrong, Hard/Good/Easy=correct
                null,                        // score (no numeric score for self-grade)
                false,                       // hintUsed
                rating,
                "self-grade-v1",
                null, null, null,            // expectedAnswerRef, graderPromptVersion, modelId
                newState.stability(),
                newState.difficulty(),
                dueAt,
                retrievabilityAtReview,
                engine.version()
        );

        // 6. Append + conditionally apply (idempotency-coupling rule from ENG-2 learnings)
        if (eventRepo.append(event)) {
            projection.applyEvent(event);
        }

        // 7. Flip CANDIDATE → SEEDED on first review
        if (isFirstReview) {
            ccRepo.flipToSeeded(conceptId);
        }

        // 8. Current retrievability with the new state
        double retrievabilityNow = engine.retrievability(newState, Instant.now());

        return new ReviewResult(retrievabilityNow, dueAt, "SEEDED");
    }
}
