package com.engram.activation;

import com.engram.TestDatabase;
import com.engram.concept.CandidateVectorRepository;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.embedding.FakeEmbeddingProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActivationServiceTest {

    private static final DataSource ds = TestDatabase.dataSource();
    private static final JdbcTemplate jdbc = new JdbcTemplate(ds);

    private FakeClaudeClient fakeClient;
    private ConceptCandidateRepository conceptRepo;
    private CandidateVectorRepository vectorRepo;
    private ActivatedCardRepository cardRepo;
    private ActivationService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        fakeClient = new FakeClaudeClient();
        conceptRepo = new ConceptCandidateRepository(jdbc);
        vectorRepo  = new CandidateVectorRepository(jdbc);
        cardRepo    = new ActivatedCardRepository(jdbc);
        service = new ActivationService(
                conceptRepo, vectorRepo, cardRepo,
                new GenerationOrchestrator(new Professor(fakeClient), new Distractor(fakeClient)));
        userId = UUID.randomUUID();
    }

    // ── 1. Migration ──────────────────────────────────────────────────────────

    @Test
    void migration_activatedCardTableExists() {
        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'activated_card'",
                Integer.class);
        assertEquals(1, count);
    }

    @Test
    void migration_activatedAtColumnOnConceptCandidate() {
        int count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'concept_candidate' AND column_name = 'activated_at'
                """, Integer.class);
        assertEquals(1, count);
    }

    // ── 2. Basic activate ─────────────────────────────────────────────────────

    @Test
    void activate_persistsCard_allFieldsCorrect() {
        UUID conceptId = insertConcept("Spaced Repetition", "memory", "SR helps long-term retention.");

        ActivatedCard card = service.activate(userId, conceptId, "idem-1");

        assertNotNull(card.cardId());
        assertEquals(conceptId, card.conceptId());
        assertEquals(userId, card.userId());
        assertFalse(card.question().isBlank(), "question must not be blank");
        assertFalse(card.correctAnswer().isBlank(), "answer must not be blank");
        assertEquals(3, card.distractors().size(), "must have exactly 3 distractors");
        assertEquals("idem-1", card.idempotencyKey());
        assertNotNull(card.createdAt());
        assertTrue(card.costMicros() >= 0);
        assertEquals(Professor.PROMPT_VERSION, card.generationPromptVersion());

        // Verify row is in DB
        Optional<ActivatedCard> dbCard = cardRepo.findByConceptId(conceptId);
        assertTrue(dbCard.isPresent());
        assertEquals(card.cardId(), dbCard.get().cardId());
    }

    @Test
    void activate_setsActivatedAtOnConceptCandidate() {
        UUID conceptId = insertConcept("Active Recall", "memory", "Testing effect.");

        service.activate(userId, conceptId, "idem-ar");

        Object activatedAt = jdbc.queryForObject(
                "SELECT activated_at FROM concept_candidate WHERE concept_id = ?",
                Object.class, conceptId);
        assertNotNull(activatedAt, "activated_at must be set after activation");
    }

    // ── 3. Generate-once (RED FIRST) ──────────────────────────────────────────

    @Test
    void generateOnce_sameConceptId_zeroLlmCallsOnSecondActivate() {
        UUID conceptId = insertConcept("Interleaving", "learning", "Mixing topics improves retention.");

        // First call: generates the card (2 LLM calls: Professor + Distractor)
        ActivatedCard first = service.activate(userId, conceptId, "idem-first");
        int callsAfterFirst = fakeClient.callCount();
        assertEquals(2, callsAfterFirst, "first activate must make exactly 2 LLM calls (Professor + Distractor)");

        fakeClient.reset();

        // Second call: same concept → cache hit, ZERO LLM calls
        ActivatedCard second = service.activate(userId, conceptId, "idem-second");
        assertEquals(0, fakeClient.callCount(),
                "second activate on same conceptId must make ZERO LLM calls — generate-once guarantee");

        // Both calls return the same card
        assertEquals(first.cardId(), second.cardId());
        assertEquals(first.question(), second.question());
    }

    // ── 4. RAG grounding: sourceSpan must appear in Professor's prompt ─────

    @Test
    void activate_ragGrounding_sourceSpanAppearsInProfessorPrompt() {
        String sourceSpan = "Retrieval practice consolidates long-term memories better than re-reading.";
        UUID conceptId = insertConcept("Retrieval Practice", "memory", sourceSpan);

        service.activate(userId, conceptId, "idem-rag");

        // Professor call is the CHEAP tier call
        FakeClaudeClient.Call professorCall = fakeClient.calls().stream()
                .filter(c -> c.tier() == ModelTier.CHEAP)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CHEAP tier call found — Professor was not called"));

        assertTrue(professorCall.userText().contains(sourceSpan),
                "Professor's prompt must contain the concept's sourceSpan for RAG grounding.\n" +
                "Expected to find: " + sourceSpan + "\nIn: " + professorCall.userText());
    }

    // ── 5. Vault-sourced distractors: neighbor context in Distractor prompt ──

    @Test
    void activate_vaultSourcedDistractors_neighborTitleAppearsInDistractorPrompt() {
        // Insert two concepts with embeddings so kNN can find neighbors
        FakeEmbeddingProvider embedder = new FakeEmbeddingProvider();

        UUID conceptAId = insertConcept("Active Recall Effect", "memory", "Testing improves retention.");
        UUID conceptBId = insertConcept("Spacing Effect", "memory", "Distributed practice works better.");
        vectorRepo.backfill(userId, embedder);

        service.activate(userId, conceptAId, "idem-vault");

        FakeClaudeClient.Call distractorCall = fakeClient.calls().stream()
                .filter(c -> c.tier() == ModelTier.EXPENSIVE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No EXPENSIVE tier call — Distractor was not called"));

        // Spacing Effect is a neighbor of Active Recall Effect; its title must appear in Distractor's grounding
        assertTrue(distractorCall.userText().contains("Spacing Effect"),
                "Distractor prompt must contain vault neighbor titles for vault-sourced grounding.\n" +
                "Expected 'Spacing Effect' in: " + distractorCall.userText());
    }

    // ── 6. Cold vault: no neighbors → still generates, no crash ──────────────

    @Test
    void activate_coldVault_noNeighbors_stillGeneratesCard() {
        // Concept with no embedding → kNN returns empty list
        UUID conceptId = insertConcept("Elaborative Interrogation", "memory", "Asking why improves learning.");
        // No backfill — embedding is NULL, kNN returns nothing

        ActivatedCard card = service.activate(userId, conceptId, "idem-cold");

        assertNotNull(card);
        assertEquals(3, card.distractors().size(), "must still produce 3 distractors on cold vault");
        assertEquals(2, fakeClient.callCount(), "cold vault must still make 2 LLM calls");
    }

    // ── 7. Orthogonal lifecycle: CANDIDATE stays CANDIDATE after activation ──

    @Test
    void activate_doesNotTouchLifecycleState() {
        UUID conceptId = insertConcept("Desirable Difficulty", "learning", "Challenges enhance learning.");

        service.activate(userId, conceptId, "idem-ortho");

        String lifecycleState = jdbc.queryForObject(
                "SELECT lifecycle_state FROM concept_candidate WHERE concept_id = ?",
                String.class, conceptId);
        assertEquals("CANDIDATE", lifecycleState,
                "activate must NOT change lifecycle_state — ACTIVATED and SEEDED are orthogonal axes");
    }

    // ── 8. No review_event written ────────────────────────────────────────────

    @Test
    void activate_doesNotWriteReviewEvent() {
        UUID conceptId = insertConcept("Generation Effect", "memory", "Creating content improves memory.");

        int beforeCount = jdbc.queryForObject("SELECT COUNT(*) FROM review_event", Integer.class);
        service.activate(userId, conceptId, "idem-noevent");
        int afterCount = jdbc.queryForObject("SELECT COUNT(*) FROM review_event", Integer.class);

        assertEquals(beforeCount, afterCount, "activation must write ZERO review_event rows");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID insertConcept(String title, String topicTag, String sourceSpan) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO concept_candidate
                    (concept_id, user_id, source_type, source_ref, source_content_hash,
                     title, topic_tag, source_span, lifecycle_state)
                VALUES (?, ?, 'OBSIDIAN_FOLDER', 'test.md', 'hash', ?, ?, ?, 'CANDIDATE')
                """, id, userId, title, topicTag, sourceSpan);
        return id;
    }
}
