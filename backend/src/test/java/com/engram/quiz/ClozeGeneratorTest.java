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

    // ── whole-word boundary tests ─────────────────────────────────────────────

    @Test
    void regexSpecialTitle_cPlusPlus_masksCorrectly() {
        ClozeCard card = gen.generate(candidate(
                "C++",
                "C++ is a widely used systems programming language."));
        assertEquals("[___] is a widely used systems programming language.", card.prompt());
        assertEquals("C++", card.answer());
    }

    @Test
    void regexSpecialTitle_parentheses_masksCorrectly() {
        ClozeCard card = gen.generate(candidate(
                "Big-O (worst case)",
                "Algorithm complexity is expressed as Big-O (worst case) notation."));
        assertEquals("Algorithm complexity is expressed as [___] notation.", card.prompt());
        assertEquals("Big-O (worst case)", card.answer());
    }

    @Test
    void titleSubstringOfLargerWord_isNotMasked_onlyWholeWordMatch() {
        // "act" must not match inside "practice"; only the standalone "act" should be blanked
        ClozeCard card = gen.generate(candidate(
                "act",
                "in practice an act solidifies learning"));
        assertEquals("in practice an [___] solidifies learning", card.prompt());
        assertEquals("act", card.answer());
    }

    @Test
    void hyphenatedNeighbor_notCorrupted_standaloneOccurrenceMasked() {
        // "retrieval" appears first as part of "retrieval-practice" (hyphenated — must NOT match),
        // then as a standalone word — only the standalone occurrence should be blanked.
        ClozeCard card = gen.generate(candidate(
                "retrieval",
                "retrieval-practice then retrieval appears."));
        assertEquals("retrieval-practice then [___] appears.", card.prompt());
        assertEquals("retrieval", card.answer());
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
