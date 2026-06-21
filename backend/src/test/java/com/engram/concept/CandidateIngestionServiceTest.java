package com.engram.concept;

import com.engram.TestDatabase;
import com.engram.embedding.FakeEmbeddingProvider;
import com.engram.ingest.IngestedDocument;
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

class CandidateIngestionServiceTest {

    private static final DataSource ds = TestDatabase.dataSource();

    @TempDir Path vault;

    private Extractor extractor;
    private FakeEmbeddingProvider embedder;
    private ConceptCandidateRepository repo;
    private CandidateIngestionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        extractor = mock(Extractor.class);
        embedder  = new FakeEmbeddingProvider();
        repo      = new ConceptCandidateRepository(new JdbcTemplate(ds));
        service   = new CandidateIngestionService(extractor, repo, embedder);
        userId = UUID.randomUUID();
    }

    // ── Core ENG-4 proof: second run on unchanged vault = 0 LLM calls ─────────

    @Test
    void secondRun_unchangedVault_zeroLlmCalls() throws IOException {
        write(vault, "note.md", "# Hello\nSome content.");
        when(extractor.extract(any())).thenReturn(List.of(new ExtractedConcept("C", "tag", "span")));

        ObsidianFolderAdapter adapter = new ObsidianFolderAdapter(vault);

        // First run — must call extractor once
        IngestionSummary first = service.ingest(adapter, userId);
        assertEquals(1, first.llmCallsMade());
        verify(extractor, times(1)).extract(any());

        reset(extractor); // clear invocation count; no stubbing needed

        // Second run — same vault, same content
        IngestionSummary second = service.ingest(adapter, userId);

        assertEquals(0, second.llmCallsMade(), "unchanged vault must make 0 LLM calls");
        verify(extractor, never()).extract(any());
        assertEquals(0, second.docsAdded());
        assertEquals(0, second.docsChanged());
        assertEquals(1, second.docsUnchanged());
    }

    // ── First run: all docs added, extractor called once per doc ─────────────

    @Test
    void firstRun_emptyPrior_extractsAllDocs() throws IOException {
        write(vault, "a.md", "Content A");
        write(vault, "b.md", "Content B");
        when(extractor.extract(any())).thenReturn(List.of(
                new ExtractedConcept("Concept 1", "t", "s"),
                new ExtractedConcept("Concept 2", "t", "s")));

        IngestionSummary summary = service.ingest(new ObsidianFolderAdapter(vault), userId);

        assertEquals(2, summary.docsAdded());
        assertEquals(0, summary.docsChanged());
        assertEquals(0, summary.docsUnchanged());
        assertEquals(4, summary.candidatesCreated());
        assertEquals(2, summary.llmCallsMade());
        verify(extractor, times(2)).extract(any());
    }

    // ── Changed doc: delete old candidates, re-extract ───────────────────────

    @Test
    void changedDoc_deletesOldCandidatesAndReExtracts() throws IOException {
        write(vault, "note.md", "original");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("Old Concept", "t", "s")));
        service.ingest(new ObsidianFolderAdapter(vault), userId);

        // Modify the file
        Files.writeString(vault.resolve("note.md"), "updated content");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("New Concept", "t", "s")));

        IngestionSummary summary = service.ingest(new ObsidianFolderAdapter(vault), userId);

        assertEquals(1, summary.docsChanged());
        assertEquals(1, summary.candidatesCreated());
        verify(extractor, times(2)).extract(any()); // once per run

        List<ConceptCandidate> found = repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(1, found.size());
        assertEquals("New Concept", found.get(0).title());
    }

    // ── Removed doc: candidates deleted, no extractor call ───────────────────

    @Test
    void removedDoc_deletesCandidates_noExtractorCall() throws IOException {
        write(vault, "keep.md", "keep");
        write(vault, "remove.md", "remove me");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("C", "t", "s")));
        service.ingest(new ObsidianFolderAdapter(vault), userId);

        // Delete the file from the vault
        Files.delete(vault.resolve("remove.md"));
        reset(extractor);
        when(extractor.extract(any())).thenReturn(List.of());

        IngestionSummary summary = service.ingest(new ObsidianFolderAdapter(vault), userId);

        assertEquals(1, summary.docsRemoved());
        // extractor called only for unchanged "keep.md" — which should be 0 (unchanged)
        verify(extractor, never()).extract(argThat(
                (IngestedDocument d) -> d.sourceRef().equals("remove.md")));
        assertTrue(repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "remove.md").isEmpty());
    }

    // ── Candidates persisted with correct fields ──────────────────────────────

    @Test
    void candidates_persistedWithCorrectFields() throws IOException {
        write(vault, "note.md", "# Test\nHello world.");
        when(extractor.extract(any())).thenReturn(
                List.of(new ExtractedConcept("My Concept", "science", "Hello world.")));

        service.ingest(new ObsidianFolderAdapter(vault), userId);

        List<ConceptCandidate> found = repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(1, found.size());
        ConceptCandidate c = found.get(0);
        assertEquals("My Concept", c.title());
        assertEquals("science", c.topicTag());
        assertEquals("Hello world.", c.sourceSpan());
        assertEquals(LifecycleState.CANDIDATE, c.lifecycleState());
        assertEquals(SourceType.OBSIDIAN_FOLDER, c.sourceType());
        assertNotNull(c.sourceContentHash());
        assertNotNull(c.conceptId());
    }

    // ── Duplicate-title dedupe: 3 concepts, 2 unique titles → 2 rows, summary == 2 ──

    @Test
    void duplicateTitlesInOneDoc_deduped_summaryAndDbAgree() throws IOException {
        write(vault, "note.md", "Some content.");
        when(extractor.extract(any())).thenReturn(List.of(
                new ExtractedConcept("Same Title", "tag-a", "span-a"),
                new ExtractedConcept("Unique Title", "tag-b", "span-b"),
                new ExtractedConcept("Same Title", "tag-c", "span-c")  // duplicate — last-wins
        ));

        IngestionSummary summary = service.ingest(new ObsidianFolderAdapter(vault), userId);

        // Summary must reflect deduped count
        assertEquals(2, summary.candidatesCreated(), "summary must count deduped concepts, not raw extractor output");

        // DB must hold exactly 2 rows
        List<ConceptCandidate> rows = repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(2, rows.size(), "DB must hold exactly 2 rows after dedupe");

        // Last-wins: "Same Title" row keeps tag-c/span-c
        ConceptCandidate sameTitle = rows.stream()
                .filter(c -> c.title().equals("Same Title"))
                .findFirst().orElseThrow();
        assertEquals("tag-c", sameTitle.topicTag());
        assertEquals("span-c", sameTitle.sourceSpan());
    }

    private static void write(Path root, String name, String content) throws IOException {
        Files.writeString(root.resolve(name), content, StandardCharsets.UTF_8);
    }
}