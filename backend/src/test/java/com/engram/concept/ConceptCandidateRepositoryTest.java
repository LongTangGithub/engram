package com.engram.concept;

import com.engram.ingest.SourceType;
import com.engram.TestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConceptCandidateRepositoryTest {

    private static final DataSource ds = TestDatabase.dataSource();

    private ConceptCandidateRepository repo;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        repo = new ConceptCandidateRepository(new JdbcTemplate(ds));
    }

    @Test
    void upsertAndFind_roundTrip() {
        UUID userId = UUID.randomUUID();
        List<ExtractedConcept> concepts = List.of(
                new ExtractedConcept("Concept A", "tag-a", "span a"),
                new ExtractedConcept("Concept B", "tag-b", "span b"));

        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "note.md", "hash-1", concepts);

        List<ConceptCandidate> found = repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(2, found.size());

        ConceptCandidate a = found.stream().filter(c -> c.title().equals("Concept A")).findFirst().orElseThrow();
        assertEquals("tag-a", a.topicTag());
        assertEquals("span a", a.sourceSpan());
        assertEquals("hash-1", a.sourceContentHash());
        assertEquals(LifecycleState.CANDIDATE, a.lifecycleState());
        assertNotNull(a.conceptId());
        assertNotNull(a.createdAt());
    }

    @Test
    void upsert_onConflict_updatesMetadata() {
        UUID userId = UUID.randomUUID();
        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "note.md", "hash-1",
                List.of(new ExtractedConcept("Same Title", "tag-old", "span-old")));

        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "note.md", "hash-2",
                List.of(new ExtractedConcept("Same Title", "tag-new", "span-new")));

        List<ConceptCandidate> found = repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "note.md");
        assertEquals(1, found.size(), "upsert must not duplicate on same title");
        assertEquals("hash-2", found.get(0).sourceContentHash());
        assertEquals("tag-new", found.get(0).topicTag());
    }

    @Test
    void deleteByDoc_removesOnlyTargetDoc() {
        UUID userId = UUID.randomUUID();
        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "a.md", "h1",
                List.of(new ExtractedConcept("C1", "t", "s")));
        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "b.md", "h2",
                List.of(new ExtractedConcept("C2", "t", "s")));

        repo.deleteByDoc(userId, SourceType.OBSIDIAN_FOLDER, "a.md");

        assertTrue(repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "a.md").isEmpty());
        assertEquals(1, repo.findByDoc(userId, SourceType.OBSIDIAN_FOLDER, "b.md").size());
    }

    @Test
    void loadPriorHashes_returnsOneHashPerSourceRef() {
        UUID userId = UUID.randomUUID();
        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "a.md", "hash-a",
                List.of(new ExtractedConcept("C1", "t", "s"),
                        new ExtractedConcept("C2", "t", "s")));
        repo.upsertAll(userId, SourceType.OBSIDIAN_FOLDER, "b.md", "hash-b",
                List.of(new ExtractedConcept("C3", "t", "s")));

        Map<String, String> hashes = repo.loadPriorHashes(userId, SourceType.OBSIDIAN_FOLDER);

        assertEquals(2, hashes.size());
        assertEquals("hash-a", hashes.get("a.md"));
        assertEquals("hash-b", hashes.get("b.md"));
    }

    @Test
    void loadPriorHashes_isolatesUsers() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        repo.upsertAll(user1, SourceType.OBSIDIAN_FOLDER, "note.md", "hash-1",
                List.of(new ExtractedConcept("C", "t", "s")));

        Map<String, String> hashes = repo.loadPriorHashes(user2, SourceType.OBSIDIAN_FOLDER);
        assertTrue(hashes.isEmpty(), "user2 must not see user1's candidates");
    }
}