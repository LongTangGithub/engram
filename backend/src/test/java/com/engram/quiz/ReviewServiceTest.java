package com.engram.quiz;

import com.engram.concept.ConceptCandidate;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.concept.LifecycleState;
import com.engram.ingest.SourceType;
import com.engram.review.ConceptSchedulerState;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.Fsrs;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ReviewService.
 * Uses embedded-postgres; no Spring context, no mocks for DB layer.
 */
class ReviewServiceTest {

    private static EmbeddedPostgres pg;
    private static DataSource ds;

    private ReviewService service;
    private ConceptCandidateRepository ccRepo;
    private ReviewEventRepository eventRepo;
    private SchedulerProjection projection;

    private UUID userId;
    private UUID conceptId;

    @BeforeAll
    static void startPostgres() throws Exception {
        pg = EmbeddedPostgres.start();
        ds = pg.getPostgresDatabase();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        ccRepo     = new ConceptCandidateRepository(jdbc);
        eventRepo  = new ReviewEventRepository(jdbc);
        projection = new SchedulerProjection(jdbc);
        service    = new ReviewService(ccRepo, eventRepo, projection, new Fsrs(), new ClozeGenerator());

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

        // due_at ≈ reviewedAt + stability days
        long expectedDays = Math.round(state.stability());
        Instant expectedDue = now.plus(expectedDays, ChronoUnit.DAYS);
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
}
