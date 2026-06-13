package com.engram.spike.concept;

import java.util.List;

/**
 * An activated concept card — the output of deep generation (Professor + Distractor).
 * This is the eyeball-quality artifact of the spike: read the question, the answer,
 * and the distractors and judge whether they are actually good on a real note.
 */
public record ActivatedCard(
        String conceptTitle,
        String question,
        String answer,
        List<String> distractors
) {}
