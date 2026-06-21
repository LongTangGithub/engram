package com.engram.embedding;

import com.engram.TestDatabase;
import com.engram.concept.CandidateIngestionService;
import com.engram.concept.CandidateVectorRepository;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.concept.ExtractedConcept;
import com.engram.concept.Extractor;
import com.engram.ingest.ObsidianFolderAdapter;
import com.engram.ingest.SourceType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmbeddingPipelineTest {

    private static final DataSource ds = TestDatabase.dataSource();
    private static final JdbcTemplate jdbc = new JdbcTemplate(ds);

    @TempDir Path vault;

    private Extractor extractor;
    private FakeEmbeddingProvider embedder;
    private ConceptCandidateRepository repo;
    private CandidateVectorRepository vectorRepo;
    private CandidateIngestionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        extractor  = mock(Extractor.class);
        embedder   = new FakeEmbeddingProvider();
        repo       = new ConceptCandidateRepository(jdbc);
        vectorRepo = new CandidateVectorRepository(jdbc);
        service    = new CandidateIngestionService(extractor, repo, embedder);
        userId     = UUID.randomUUID();
    }

    // ── 1. Migration: pgvector extension + column + HNSW index ───────────────

    @Test
    void migration_pgvectorExtensionPresent() {
        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertEquals(1, count, "pgvector extension must be installed");
    }

    @Test
    void migration_embeddingColumnIsVector1536() {
        // information_schema reports the underlying udt_name for domain/custom types
        String udtName = jdbc.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_name = 'concept_candidate' AND column_name = 'embedding'
                """, String.class);
        assertEquals("vector", udtName, "embedding column must be vector type");
    }

    @Test
    void migration_hnswIndexExists() {
        int count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'concept_candidate'
                  AND indexname = 'concept_candidate_embedding_hnsw'
                """, Integer.class);
        assertEquals(1, count, "HNSW index must exist on concept_candidate.embedding");
    }

    // ── 2. Ingest: ADDED → embedding written, model + embedded_at populated ──

    @Test
    void ingest_addedDoc_writesEmbeddingAndMetadata() throws IOException {
        write(vault, "note.md", "Spaced repetition helps long-term memory.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Spaced Repetition", "memory", "helps long-term memory")));

        service.ingest(new ObsidianFolderAdapter(vault), userId);

        var row = jdbc.queryForMap(
                "SELECT embedding, embedding_model, embedded_at FROM concept_candidate WHERE user_id = ?",
                userId);

        assertNotNull(row.get("embedding"),       "embedding must not be null after ingest");
        assertNotNull(row.get("embedding_model"), "embedding_model must not be null");
        assertNotNull(row.get("embedded_at"),     "embedded_at must not be null");
        assertEquals(embedder.modelId(), row.get("embedding_model").toString());
    }

    @Test
    void ingest_embeddingHasCorrectDimension() throws IOException {
        write(vault, "note.md", "Active recall strengthens memory traces.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Active Recall", "memory", "strengthens memory traces")));

        service.ingest(new ObsidianFolderAdapter(vault), userId);

        // pgvector stores dimension in its binary format; read back as string and count commas
        String vecStr = jdbc.queryForObject(
                "SELECT embedding::text FROM concept_candidate WHERE user_id = ?",
                String.class, userId);
        assertNotNull(vecStr);
        // "[v1,v2,...,v1536]" — count commas: dimension - 1 commas
        long commas = vecStr.chars().filter(c -> c == ',').count();
        assertEquals(1535, commas, "vector must have 1536 dimensions");
    }

    // ── 3. CRITICAL: unchanged vault → zero embedding calls ──────────────────

    @Test
    void resync_unchangedVault_zeroEmbeddingCalls() throws IOException {
        write(vault, "note.md", "Retrieval practice effect.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Retrieval Practice", "memory", "practice effect")));

        // First sync — embeds once
        service.ingest(new ObsidianFolderAdapter(vault), userId);
        int firstRunCount = embedder.callCount();
        assertTrue(firstRunCount > 0, "first sync must embed at least one concept");

        embedder.resetCallCount();

        // Second sync — same vault, same content
        service.ingest(new ObsidianFolderAdapter(vault), userId);

        assertEquals(0, embedder.callCount(),
                "unchanged vault must trigger ZERO embedding calls — embedding is gated on ADDED/CHANGED");
    }

    // ── 4. kNN: nearest-neighbor order, user-scoped, excludes self ───────────

    @Test
    void findNearestNeighbors_returnsCorrectOrderAndExcludesSelf() throws IOException {
        // Seed 3 concepts. FakeEmbeddingProvider is deterministic — texts with similar
        // hash values will produce more similar vectors. We use specific strings to get
        // a predictable ranking.
        write(vault, "a.md", "alpha");
        write(vault, "b.md", "beta");
        write(vault, "c.md", "gamma");
        when(extractor.extract(argThat(d -> d != null && d.sourceRef().equals("a.md")))).thenReturn(
                List.of(new ExtractedConcept("Alpha", "t", "alpha text")));
        when(extractor.extract(argThat(d -> d != null && d.sourceRef().equals("b.md")))).thenReturn(
                List.of(new ExtractedConcept("Beta", "t", "alpha text variant")));  // most similar to Alpha
        when(extractor.extract(argThat(d -> d != null && d.sourceRef().equals("c.md")))).thenReturn(
                List.of(new ExtractedConcept("Gamma", "t", "completely unrelated xyz123")));

        service.ingest(new ObsidianFolderAdapter(vault), userId);

        // Retrieve query concept ID (Alpha)
        UUID alphaId = UUID.fromString(jdbc.queryForObject(
                "SELECT concept_id FROM concept_candidate WHERE user_id = ? AND title = 'Alpha'",
                String.class, userId));

        List<UUID> neighbors = vectorRepo.findNearestNeighbors(userId, alphaId, 2);

        assertEquals(2, neighbors.size(), "must return k=2 neighbors");
        assertFalse(neighbors.contains(alphaId), "must exclude the query concept itself");
    }

    @Test
    void findNearestNeighbors_scopedToUser_doesNotReturnOtherUsersResults() throws IOException {
        // User 1 has a concept
        write(vault, "note.md", "Memory consolidation during sleep.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Sleep Memory", "memory", "consolidation during sleep")));
        service.ingest(new ObsidianFolderAdapter(vault), userId);

        // User 2 has the same concept
        UUID userId2 = UUID.randomUUID();
        CandidateIngestionService service2 = new CandidateIngestionService(extractor, repo, embedder);
        Path vault2 = Files.createTempDirectory("vault2");
        write(vault2, "note.md", "Memory consolidation during sleep.");
        service2.ingest(new ObsidianFolderAdapter(vault2), userId2);

        // Add a second concept for user 1 so we have something to query against
        write(vault, "note2.md", "Hippocampus encodes episodic memories.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Hippocampus", "memory", "encodes episodic memories")));
        service.ingest(new ObsidianFolderAdapter(vault), userId);

        UUID queryId = UUID.fromString(jdbc.queryForObject(
                "SELECT concept_id FROM concept_candidate WHERE user_id = ? AND title = 'Sleep Memory'",
                String.class, userId));

        List<UUID> neighbors = vectorRepo.findNearestNeighbors(userId, queryId, 10);

        // Must not contain user2's concepts
        List<UUID> user2Ids = jdbc.query(
                "SELECT concept_id FROM concept_candidate WHERE user_id = ?",
                (rs, n) -> UUID.fromString(rs.getString("concept_id")),
                userId2);
        for (UUID u2id : user2Ids) {
            assertFalse(neighbors.contains(u2id), "kNN must not return another user's concepts");
        }
    }

    // ── 5. Backfill: NULL embeddings filled; second run is no-op ─────────────

    @Test
    void backfill_fillsNullEmbeddings_thenIdempotent() {
        // Insert two candidates directly with NULL embedding (simulates pre-pipeline rows)
        jdbc.update("""
                INSERT INTO concept_candidate (user_id, source_type, source_ref, source_content_hash,
                    title, topic_tag, source_span, lifecycle_state)
                VALUES (?, 'OBSIDIAN_FOLDER', 'ref.md', 'hash1', 'Concept A', 't', 'span a', 'CANDIDATE'),
                       (?, 'OBSIDIAN_FOLDER', 'ref.md', 'hash1', 'Concept B', 't', 'span b', 'CANDIDATE')
                """, userId, userId);

        int beforeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM concept_candidate WHERE user_id = ? AND embedding IS NULL",
                Integer.class, userId);
        assertEquals(2, beforeCount);

        // First backfill: fills both
        int filled = vectorRepo.backfill(userId, embedder);
        assertEquals(2, filled);

        int afterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM concept_candidate WHERE user_id = ? AND embedding IS NULL",
                Integer.class, userId);
        assertEquals(0, afterCount, "all embeddings must be filled after backfill");

        embedder.resetCallCount();

        // Second backfill: idempotent — nothing to do
        int second = vectorRepo.backfill(userId, embedder);
        assertEquals(0, second, "second backfill must embed nothing (idempotent)");
        assertEquals(0, embedder.callCount(), "second backfill must make zero embedding calls");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static void write(Path root, String name, String content) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(name), content, StandardCharsets.UTF_8);
    }
}
