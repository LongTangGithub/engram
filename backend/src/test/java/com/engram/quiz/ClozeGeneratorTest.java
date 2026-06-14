package com.engram.quiz;

import com.engram.concept.ConceptCandidate;
import com.engram.concept.LifecycleState;
import com.engram.ingest.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClozeGeneratorTest {

    private final ClozeGenerator gen = new ClozeGenerator();

    @Test
    void titlePresentInSpan_maskedCaseInsensitive() {
        ClozeCard card = gen.generate(candidate(
                "Spaced Repetition",
                "Spaced repetition is a technique for memorizing information efficiently."));

        assertEquals("[___] is a technique for memorizing information efficiently.", card.prompt());
        assertEquals("Spaced Repetition", card.answer());
    }

    @Test
    void titleAbsentFromSpan_masksLongestWord() {
        ClozeCard card = gen.generate(candidate(
                "Forgetting Curve",
                "Memory fades exponentially over time without review."));

        // longest word is "exponentially" (13 chars)
        assertTrue(card.prompt().contains(ClozeGenerator.BLANK));
        assertEquals("exponentially", card.answer());
        assertFalse(card.prompt().contains("exponentially"),
                "masked word must not appear in prompt");
    }

    @Test
    void titleInMiddleOfSpan_promptPreservesContext() {
        ClozeCard card = gen.generate(candidate(
                "retrieval",
                "Active retrieval strengthens memory traces."));

        assertEquals("Active [___] strengthens memory traces.", card.prompt());
        assertEquals("retrieval", card.answer());
    }

    @Test
    void conceptIdPassedThrough() {
        UUID id = UUID.randomUUID();
        ClozeCard card = gen.generate(candidateWithId(id, "Term", "Term is important."));
        assertEquals(id, card.conceptId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ConceptCandidate candidate(String title, String span) {
        return candidateWithId(UUID.randomUUID(), title, span);
    }

    private static ConceptCandidate candidateWithId(UUID id, String title, String span) {
        return new ConceptCandidate(id, UUID.randomUUID(), SourceType.OBSIDIAN_FOLDER,
                "note.md", "hash", title, "tag", span, LifecycleState.CANDIDATE,
                Instant.now(), Instant.now());
    }
}
