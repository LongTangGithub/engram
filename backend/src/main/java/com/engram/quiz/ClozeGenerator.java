package com.engram.quiz;

import com.engram.concept.ConceptCandidate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Deterministic cloze card generator — no AI, no DB.
 *
 * Masking rule (in order):
 *   1. Mask the candidate title where it appears in source_span (case-insensitive, first match).
 *   2. If title not found, mask the longest word in source_span.
 *
 * Quality note: this is intentionally basic for ENG-6. The Professor pipeline (ENG-8) will
 * produce higher-quality cloze prompts from the activated card's structured content.
 */
public class ClozeGenerator {

    static final String BLANK = "[___]";

    public ClozeCard generate(ConceptCandidate candidate) {
        String span = candidate.sourceSpan();
        if (span == null || span.isBlank()) {
            span = candidate.title();
        }

        String masked = maskByTitle(span, candidate.title());
        if (masked != null) {
            return new ClozeCard(candidate.conceptId(), masked, candidate.title());
        }

        String longestWord = longestWord(span);
        String prompt = maskFirst(span, longestWord);
        return new ClozeCard(candidate.conceptId(), prompt, longestWord);
    }

    /**
     * Replaces the first whole-word, case-insensitive occurrence of {@code title} in {@code span}.
     * Returns null if no whole-word match is found.
     *
     * "Whole word" means the match is not immediately preceded or followed by a word character
     * or a hyphen. Treating hyphens as word-connectors prevents masking a title that appears
     * only as part of a hyphenated compound (e.g. "retrieval" does not match in "retrieval-practice").
     *
     * {@code answer} is always the canonical {@link ConceptCandidate#title()} stored in the DB —
     * not the matched surface form — so casing is preserved as the author entered it.
     */
    private static String maskByTitle(String span, String title) {
        Pattern p = Pattern.compile(
                "(?<![\\w-])" + Pattern.quote(title) + "(?![\\w-])",
                Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(span);
        if (!m.find()) return null;
        return span.substring(0, m.start()) + BLANK + span.substring(m.end());
    }

    private static String maskFirst(String span, String word) {
        // longestWord() splits on \W+ so the result is pure [a-zA-Z0-9_]; \b is reliable here.
        java.util.regex.Matcher m = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(span);
        if (!m.find()) return BLANK;
        return span.substring(0, m.start()) + BLANK + span.substring(m.end());
    }

    private static String longestWord(String span) {
        return Arrays.stream(span.split("\\W+"))
                .filter(w -> !w.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElse(span.trim());
    }
}
