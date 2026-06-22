package com.engram.dashboard;

import com.engram.TestDatabase;
import com.engram.activation.ActivatedCardRepository;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.concept.ExtractedConcept;
import com.engram.ingest.SourceType;
import com.engram.quiz.ClozeGenerator;
import com.engram.quiz.ReviewService;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.Fsrs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {

    private static final DataSource ds = TestDatabase.dataSource();

    private DashboardService dashboard;
    private ReviewService reviewService;
    private ConceptCandidateRepository ccRepo;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();

        JdbcTemplate jdbc     = new JdbcTemplate(ds);
        Fsrs fsrs             = new Fsrs();
        ccRepo                = new ConceptCandidateRepository(jdbc);
        ReviewEventRepository eventRepo = new ReviewEventRepository(jdbc);
        SchedulerProjection projection  = new SchedulerProjection(jdbc);

        reviewService = new ReviewService(ccRepo, eventRepo, projection, fsrs, new ClozeGenerator(),
                new ActivatedCardRepository(jdbc));
        dashboard     = new DashboardService(new DashboardRepository(jdbc), fsrs);

        userId = UUID.randomUUID();
    }

    // ── mode flip ────────────────────────────────────────────────────────────

    @Test
    void noSeededConcepts_modeisColdStart() {
        seedCandidates("memory", List.of("Spaced Repetition", "Retrieval Practice"));
        DashboardView view = dashboard.getDashboard(userId);

        assertEquals(DashboardMode.COLD_START, view.mode());
        assertEquals(2, view.frontierCount());
        assertEquals(0, view.seededCount());
        assertNull(view.livingKnowledgePct());
    }

    @Test
    void afterFirstReview_modeFlipsToSteadyState() {
        seedCandidates("memory", List.of("Spaced Repetition", "Retrieval Practice"));
        UUID conceptId = firstConceptId("memory");

        reviewService.submitReview(userId, conceptId, 3, UUID.randomUUID().toString(), Instant.now());

        DashboardView view = dashboard.getDashboard(userId);
        assertEquals(DashboardMode.STEADY_STATE, view.mode());
        assertEquals(1, view.seededCount());
        assertEquals(1, view.frontierCount());
        assertNotNull(view.livingKnowledgePct());
    }

    // ── unseeded concept → null retrievability/tier ──────────────────────────

    @Test
    void unseededConcept_nullRetrievabilityAndTier() {
        seedCandidates("botany", List.of("Photosynthesis"));
        DashboardView view = dashboard.getDashboard(userId);

        ConceptView concept = findConcept(view, "Photosynthesis");
        assertNotNull(concept);
        assertNull(concept.retrievability(), "unseeded must have null retrievability, not zero");
        assertNull(concept.moodTier(),        "unseeded must have null tier, not DORMANT");
        assertEquals("CANDIDATE", concept.lifecycleState());
    }

    // ── seeded concepts map to correct tiers ─────────────────────────────────

    @Test
    void thrivingConcept_reviewedNow() {
        seedCandidates("memory", List.of("Thriving"));
        UUID id = firstConceptId("memory");
        reviewService.submitReview(userId, id, 3, UUID.randomUUID().toString(), Instant.now());

        ConceptView c = findConcept(dashboard.getDashboard(userId), "Thriving");
        assertNotNull(c.retrievability());
        assertTrue(c.retrievability() >= 0.9, "R should be >= 0.9 when reviewed just now");
        assertEquals(MoodTier.THRIVING, c.moodTier());
    }

    @Test
    void fadingConcept_againRating3DaysAgo() {
        seedCandidates("memory", List.of("Fading"));
        UUID id = firstConceptId("memory");
        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);
        reviewService.submitReview(userId, id, 1, UUID.randomUUID().toString(), threeDaysAgo);

        ConceptView c = findConcept(dashboard.getDashboard(userId), "Fading");
        assertNotNull(c.retrievability());
        assertTrue(c.retrievability() >= 0.5 && c.retrievability() < 0.7,
                "Again @ 3d elapsed → FADING; got R=" + c.retrievability());
        assertEquals(MoodTier.FADING, c.moodTier());
    }

    @Test
    void dormantConcept_againRating600DaysAgo() {
        seedCandidates("memory", List.of("Dormant"));
        UUID id = firstConceptId("memory");
        Instant longAgo = Instant.now().minus(600, ChronoUnit.DAYS);
        reviewService.submitReview(userId, id, 1, UUID.randomUUID().toString(), longAgo);

        ConceptView c = findConcept(dashboard.getDashboard(userId), "Dormant");
        assertNotNull(c.retrievability());
        assertTrue(c.retrievability() < 0.3,
                "Again @ 600d elapsed → DORMANT; got R=" + c.retrievability());
        assertEquals(MoodTier.DORMANT, c.moodTier());
    }

    // ── livingKnowledgePct math ───────────────────────────────────────────────

    @Test
    void livingKnowledgePct_onlyRetrievableConceptsCounted() {
        // 1 THRIVING (retrievable), 1 DORMANT (not retrievable), 1 unseeded (frontier)
        seedCandidates("memory", List.of("Thriving", "Dormant", "Unseeded"));

        UUID thrivingId = conceptIdByTitle("memory", "Thriving");
        UUID dormantId  = conceptIdByTitle("memory", "Dormant");

        reviewService.submitReview(userId, thrivingId, 3, UUID.randomUUID().toString(), Instant.now());
        reviewService.submitReview(userId, dormantId,  1, UUID.randomUUID().toString(),
                Instant.now().minus(600, ChronoUnit.DAYS));

        DashboardView view = dashboard.getDashboard(userId);
        assertEquals(DashboardMode.STEADY_STATE, view.mode());
        assertEquals(2, view.seededCount());
        assertEquals(1, view.retrievableCount(), "only THRIVING concept is retrievable (R>=0.7)");
        assertEquals(1, view.frontierCount());
        // livingKnowledgePct = 1/2 * 100 = 50.0
        assertNotNull(view.livingKnowledgePct());
        assertEquals(50.0, view.livingKnowledgePct(), 0.01);
    }

    // ── garden grouping + rollup ──────────────────────────────────────────────

    @Test
    void gardens_groupedByTopicTag() {
        seedCandidates("memory",  List.of("Spaced Repetition"));
        seedCandidates("botany",  List.of("Photosynthesis"));

        DashboardView view = dashboard.getDashboard(userId);
        assertEquals(2, view.gardens().size());

        GardenView memGarden = view.gardens().stream()
                .filter(g -> "memory".equals(g.topicTag())).findFirst().orElseThrow();
        GardenView botGarden = view.gardens().stream()
                .filter(g -> "botany".equals(g.topicTag())).findFirst().orElseThrow();

        assertEquals(1, memGarden.totalCount());
        assertEquals(1, botGarden.totalCount());
    }

    @Test
    void allUnseededGarden_nullRollup() {
        seedCandidates("botany", List.of("Photosynthesis", "Respiration"));
        DashboardView view = dashboard.getDashboard(userId);
        GardenView g = view.gardens().get(0);
        assertNull(g.avgRetrievability(), "all-unseeded garden rollup must be null");
        assertNull(g.rolledUpTier(),       "all-unseeded garden tier must be null");
        assertEquals(2, g.frontierCount());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedCandidates(String tag, List<String> titles) {
        String ref = tag + ".md";
        List<ExtractedConcept> concepts = titles.stream()
                .map(t -> new ExtractedConcept(t, tag, t + " is an important concept."))
                .toList();
        ccRepo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, ref, "hash-" + tag, concepts);
    }

    private UUID firstConceptId(String tag) {
        return ccRepo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, tag + ".md")
                .get(0).conceptId();
    }

    private UUID conceptIdByTitle(String tag, String title) {
        return ccRepo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, tag + ".md").stream()
                .filter(c -> title.equals(c.title()))
                .findFirst().orElseThrow().conceptId();
    }

    private static ConceptView findConcept(DashboardView view, String title) {
        return view.gardens().stream()
                .flatMap(g -> g.concepts().stream())
                .filter(c -> title.equals(c.title()))
                .findFirst().orElse(null);
    }
}
