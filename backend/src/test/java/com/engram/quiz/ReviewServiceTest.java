package com.engram.quiz;

import com.engram.TestDatabase;
import com.engram.activation.ActivatedCard;
import com.engram.activation.ActivatedCardRepository;
import com.engram.concept.ConceptCandidate;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.concept.LifecycleState;
import com.engram.ingest.SourceType;
import com.engram.review.ConceptSchedulerState;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.Fsrs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceTest {

    private static final DataSource ds = TestDatabase.dataSource();

    private ReviewService service;
    private ConceptCandidateRepository ccRepo;
    private ReviewEventRepository eventRepo;
    private SchedulerProjection projection;
    private ActivatedCardRepository cardRepo;
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID conceptId;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();

        jdbc       = new JdbcTemplate(ds);
        ccRepo     = new ConceptCandidateRepository(jdbc);
        eventRepo  = new ReviewEventRepository(jdbc);
        projection = new SchedulerProjection(jdbc);
        cardRepo   = new ActivatedCardRepository(jdbc);
        service    = new ReviewService(ccRepo, eventRepo, projection, new Fsrs(), new ClozeGenerator(), cardRepo);

        userId    = UUID.randomUUID();
        conceptId = UUID.randomUUID();

        // Seed one candidate so nextCard / submitReview have something to work with
        ccRepo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "note.md", "hash-1",
                List.of(new com.engram.concept.ExtractedConcept(
                        "Spaced Repetition",
                        "memory",
                        "Spaced repetition is a learning technique.")));

        // Grab the generated conceptId from the DB
        var candidates = ccRepo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        conceptId = candidates.get(0).conceptId();
    }

    // ── nextCard ─────────────────────────────────────────────────────────────

    @Test
    void nextCard_withCandidate_returnsClozeCard() {
        var card = service.nextCard(userId);
        assertTrue(card.isPresent());
        assertTrue(card.get().prompt().contains("[___]"),
                "prompt must contain blank");
        assertFalse(card.get().answer().isBlank());
    }

    @Test
    void nextCard_noCandidate_returnsEmpty() {
        var card = service.nextCard(UUID.randomUUID()); // unknown user
        assertTrue(card.isEmpty());
    }

    // ── submitReview: first review ────────────────────────────────────────────

    @Test
    void submitReview_firstReview_writesEventAndProjection() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String clientEventId = UUID.randomUUID().toString();

        ReviewResult result = service.submitReview(userId, conceptId, 3, clientEventId, now);

        // Result is well-formed
        assertTrue(result.retrievabilityNow() > 0.0 && result.retrievabilityNow() <= 1.0);
        assertNotNull(result.dueAt());
        assertEquals("SEEDED", result.lifecycleState());

        // Projection row exists with correct fields
        ConceptSchedulerState state = projection.read(conceptId, userId);
        assertNotNull(state, "projection row must exist after first review");
        assertEquals(1, state.reviewCount());
        assertNotNull(state.stability());
        assertNotNull(state.difficulty());

        // due_at = reviewedAt + stability seconds (second precision, not whole-day rounding)
        Instant expectedDue = now.plusSeconds(Math.round(state.stability() * 86400));
        assertEquals(expectedDue, result.dueAt());

        // lifecycle flipped to SEEDED
        var candidates = ccRepo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(LifecycleState.SEEDED, candidates.get(0).lifecycleState());
    }

    // ── submitReview: idempotency ─────────────────────────────────────────────

    @Test
    void submitReview_sameClientEventId_isIdempotent() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String clientEventId = UUID.randomUUID().toString();

        service.submitReview(userId, conceptId, 3, clientEventId, now);
        service.submitReview(userId, conceptId, 3, clientEventId, now); // duplicate

        // Projection must reflect exactly ONE review
        ConceptSchedulerState state = projection.read(conceptId, userId);
        assertEquals(1, state.reviewCount(),
                "duplicate clientEventId must not double-apply the projection");
    }

    // ── submitReview: second review advances stability ────────────────────────

    @Test
    void submitReview_goodRatingOnSeededConcept_advancesStability() {
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // First review — Good
        service.submitReview(userId, conceptId, 3, UUID.randomUUID().toString(), t0);
        double stabilityAfterFirst = projection.read(conceptId, userId).stability();

        // Second review — Good, 5 days later
        Instant t1 = t0.plus(5, ChronoUnit.DAYS);
        service.submitReview(userId, conceptId, 3, UUID.randomUUID().toString(), t1);
        double stabilityAfterSecond = projection.read(conceptId, userId).stability();

        assertTrue(stabilityAfterSecond > stabilityAfterFirst,
                "Good review must increase stability; was " + stabilityAfterFirst
                        + " → " + stabilityAfterSecond);
        assertEquals(2, projection.read(conceptId, userId).reviewCount());
    }

    // ── ENG-9: MCQ auto-grading (server-side) ─────────────────────────────────

    private static final String CORRECT = "Long-term memory retention";
    private static final List<String> DISTRACTORS =
            List.of("Short-term memorization", "Random guessing", "Passive re-reading");

    /** Persists an activated MCQ card for the seeded concept so the auto-grade path has a target. */
    private void seedActivatedCard() {
        cardRepo.save(new ActivatedCard(
                UUID.randomUUID(), conceptId, userId,
                "What does spaced repetition optimize?", CORRECT, DISTRACTORS,
                "fake-model", "professor-v1/distractor-v1",
                0, 0, 0L, "idem-" + conceptId, Instant.now()));
    }

    private Map<String, Object> latestEvent() {
        return jdbc.queryForMap(
                "SELECT is_correct, format, grading_scheme_version, fsrs_rating, expected_answer_ref "
                        + "FROM review_event WHERE concept_id = ? ORDER BY seq DESC LIMIT 1", conceptId);
    }

    @Test
    void submitMcq_correctPick_recordsGoodRating_andIsCorrectTrue() {
        seedActivatedCard();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        McqGradeResult result = service.submitMcqReview(
                userId, conceptId, CORRECT, UUID.randomUUID().toString(), now);

        // Server-decided outcome surfaced to the caller
        assertTrue(result.isCorrect(), "correct pick must grade as correct");
        assertEquals(CORRECT, result.correctAnswer(), "correct answer revealed post-commit");
        assertTrue(result.retrievabilityNow() > 0.0);

        // Event: raw outcome is the truth; rating is the derived interpretation
        Map<String, Object> e = latestEvent();
        assertEquals(true, e.get("is_correct"));
        assertEquals("mcq", e.get("format"));
        assertEquals("mcq-auto-v1", e.get("grading_scheme_version"));
        assertEquals(3, ((Number) e.get("fsrs_rating")).intValue(), "correct → Good (3)");

        // FSRS advanced as a real review
        ConceptSchedulerState state = projection.read(conceptId, userId);
        assertEquals(1, state.reviewCount());
        assertNotNull(state.stability());
    }

    @Test
    void submitMcq_wrongPick_recordsAgainRating_andIsCorrectFalse() {
        seedActivatedCard();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        McqGradeResult result = service.submitMcqReview(
                userId, conceptId, DISTRACTORS.get(0), UUID.randomUUID().toString(), now);

        assertFalse(result.isCorrect(), "wrong pick must grade as incorrect");
        assertEquals(CORRECT, result.correctAnswer());

        Map<String, Object> e = latestEvent();
        assertEquals(false, e.get("is_correct"));
        assertEquals("mcq", e.get("format"));
        assertEquals("mcq-auto-v1", e.get("grading_scheme_version"));
        assertEquals(1, ((Number) e.get("fsrs_rating")).intValue(), "wrong → Again (1)");
    }

    @Test
    void submitMcq_gradesServerSide_clientNeverSuppliesCorrectAnswer() {
        // The method signature has NO correctAnswer parameter — the only way this grades correctly
        // is if the server holds the answer. A case/whitespace-variant pick still grades correct,
        // proving the comparison is server-side against the stored card (not a client echo).
        seedActivatedCard();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        McqGradeResult result = service.submitMcqReview(
                userId, conceptId, "  long-term   memory RETENTION ",
                UUID.randomUUID().toString(), now);

        assertTrue(result.isCorrect(), "normalized case/whitespace variant must match server-side");
        assertEquals(1, projection.read(conceptId, userId).reviewCount());
    }

    @Test
    void submitMcq_sameClientEventId_isIdempotent() {
        seedActivatedCard();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String clientEventId = UUID.randomUUID().toString();

        service.submitMcqReview(userId, conceptId, CORRECT, clientEventId, now);
        service.submitMcqReview(userId, conceptId, CORRECT, clientEventId, now); // duplicate

        assertEquals(1, projection.read(conceptId, userId).reviewCount(),
                "duplicate clientEventId must not double-apply the projection");
        Integer eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_event WHERE concept_id = ?", Integer.class, conceptId);
        assertEquals(1, eventCount, "duplicate clientEventId must write exactly one event");
    }

    @Test
    void submitMcq_noActivatedCard_throws() {
        // Concept exists as a candidate but was never activated → no card to grade against.
        assertThrows(IllegalArgumentException.class,
                () -> service.submitMcqReview(userId, conceptId, CORRECT,
                        UUID.randomUUID().toString(), Instant.now()));
    }
}
