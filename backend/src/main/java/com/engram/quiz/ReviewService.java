package com.engram.quiz;

import com.engram.activation.ActivatedCard;
import com.engram.activation.ActivatedCardRepository;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.review.ConceptSchedulerState;
import com.engram.review.ReviewEvent;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.FsrsState;
import com.engram.scheduler.RetrievabilityEngine;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class ReviewService {

    /** ENG-9: versioned interpretation tag for click-to-grade MCQ outcomes. */
    static final String MCQ_GRADING_SCHEME_VERSION = "mcq-auto-v1";

    private final ConceptCandidateRepository ccRepo;
    private final ReviewEventRepository eventRepo;
    private final SchedulerProjection projection;
    private final RetrievabilityEngine engine;
    private final ClozeGenerator clozeGenerator;
    private final ActivatedCardRepository cardRepo;

    public ReviewService(ConceptCandidateRepository ccRepo,
                         ReviewEventRepository eventRepo,
                         SchedulerProjection projection,
                         RetrievabilityEngine engine,
                         ClozeGenerator clozeGenerator,
                         ActivatedCardRepository cardRepo) {
        this.ccRepo = ccRepo;
        this.eventRepo = eventRepo;
        this.projection = projection;
        this.engine = engine;
        this.clozeGenerator = clozeGenerator;
        this.cardRepo = cardRepo;
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
        // Self-grade: isCorrect is derived from the rating (Again=wrong, Hard/Good/Easy=correct).
        return record(userId, conceptId, rating, rating > 1, clientEventId, reviewedAt,
                format, "self-grade-v1", null);
    }

    /**
     * ENG-9: auto-grades an MCQ pick SERVER-SIDE and advances the FSRS state.
     *
     * The client sends which option the user picked; the server is the only party that knows the
     * correct answer (the pre-commit card payload never ships it — Law 1). Correctness is decided
     * here by comparing the pick to the stored correct answer.
     *
     * Mapping (locked): correct → Good (3), wrong → Again (1). Never Easy — recognition has
     * scaffolding, so Easy would inflate stability and under-schedule. Response-time mapping is a
     * future seam, not built here.
     *
     * The raw outcome (isCorrect) is the truth recorded in is_correct; the 1/3 rating is the
     * versioned interpretation (grading_scheme_version = "mcq-auto-v1").
     *
     * @param selectedOption the option text the user picked (matched case/whitespace-insensitive
     *                       against the stored correct answer — see {@link #normalize})
     */
    public McqGradeResult submitMcqReview(UUID userId, UUID conceptId, String selectedOption,
                                          String clientEventId, Instant reviewedAt) {
        ActivatedCard card = cardRepo.findByConceptId(conceptId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no activated card for concept: " + conceptId));

        boolean isCorrect = normalize(selectedOption).equals(normalize(card.correctAnswer()));
        int rating = isCorrect ? 3 : 1;

        // expected_answer_ref = the card whose correctAnswer was the grading target (a pointer,
        // not the literal answer — keeps the answer string out of the cheap-to-scan grade columns).
        ReviewResult result = record(userId, conceptId, rating, isCorrect, clientEventId, reviewedAt,
                "mcq", MCQ_GRADING_SCHEME_VERSION, card.cardId().toString());

        return new McqGradeResult(result.retrievabilityNow(), result.dueAt(), result.lifecycleState(),
                isCorrect, card.correctAnswer());
    }

    /** Model-generated option strings are matched on exact text, normalized for case + whitespace. */
    private static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Shared write path for both self-grade and auto-grade reviews: build the immutable event,
     * append-then-apply (idempotency-coupling rule from ENG-2), flip lifecycle on first review.
     *
     * @param isCorrect raw outcome (the truth) — for MCQ it is the real correctness, for self-grade
     *                  it is derived from the rating
     */
    private ReviewResult record(UUID userId, UUID conceptId, int rating, boolean isCorrect,
                                String clientEventId, Instant reviewedAt, String format,
                                String gradingSchemeVersion, String expectedAnswerRef) {
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
                isCorrect,                   // raw outcome — the truth
                null,                        // score (no numeric score: binary outcome)
                false,                       // hintUsed
                rating,
                gradingSchemeVersion,
                expectedAnswerRef, null, null, // expectedAnswerRef, graderPromptVersion, modelId
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
