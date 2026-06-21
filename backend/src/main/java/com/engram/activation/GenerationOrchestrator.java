package com.engram.activation;

import com.engram.concept.ConceptCandidate;

import java.util.List;
import java.util.logging.Logger;

// Fixed pipeline: Professor (cheap) → Distractor (expensive) → verify.
// Vault-sourced distractors arrive as neighborConcepts — kNN lookup is the caller's job.
// Cold vault (empty neighbors): grounding falls back to concept's own sourceSpan only.
public class GenerationOrchestrator {

    private static final Logger log = Logger.getLogger(GenerationOrchestrator.class.getName());

    private final Professor professor;
    private final Distractor distractor;

    public GenerationOrchestrator(Professor professor, Distractor distractor) {
        this.professor = professor;
        this.distractor = distractor;
    }

    public GenerationResult generate(ConceptCandidate concept, List<ConceptCandidate> neighbors) {
        // Step 1: draft Q+A (cheap)
        Professor.Draft draft = professor.draft(concept);

        // Step 2: build grounding note for Distractor
        // Vault-sourced context makes distractors sound like real things from the user's knowledge base.
        String groundingNote = buildDistractorGrounding(concept, neighbors);
        if (neighbors.isEmpty()) {
            log.info("cold vault — no neighbors for concept " + concept.conceptId() + "; distractor falls back to sourceSpan only");
        }

        // Step 3: write distractors (expensive — vault-sourced when neighbors present)
        Distractor.Result distractorResult = distractor.write(draft.question(), draft.answer(), groundingNote);

        // Step 4: verify
        verify(draft, distractorResult.distractors());

        // Step 5: sum cost across both calls
        int totalInput = draft.usage().inputTokens() + distractorResult.usage().inputTokens();
        int totalOutput = draft.usage().outputTokens() + distractorResult.usage().outputTokens();
        long profCost = ModelTier.CHEAP.toMicros(draft.usage().inputTokens(), draft.usage().outputTokens());
        long distCost = ModelTier.EXPENSIVE.toMicros(distractorResult.usage().inputTokens(), distractorResult.usage().outputTokens());

        return new GenerationResult(
                draft.question(),
                draft.answer(),
                distractorResult.distractors(),
                ModelTier.EXPENSIVE.modelId,  // Distractor model dominates cost; stored for audit
                totalInput,
                totalOutput,
                profCost + distCost);
    }

    private static String buildDistractorGrounding(ConceptCandidate concept, List<ConceptCandidate> neighbors) {
        if (neighbors.isEmpty()) {
            return concept.sourceSpan();
        }
        StringBuilder sb = new StringBuilder(concept.sourceSpan());
        sb.append("\n\nRELATED VAULT CONCEPTS (use as inspiration for plausible-but-wrong distractors):");
        for (ConceptCandidate n : neighbors) {
            sb.append("\n- ").append(n.title()).append(": ").append(n.sourceSpan());
        }
        return sb.toString();
    }

    private static void verify(Professor.Draft draft, List<String> distractors) {
        if (draft.question() == null || draft.question().isBlank()) {
            throw new IllegalStateException("verify: empty question");
        }
        if (draft.answer() == null || draft.answer().isBlank()) {
            throw new IllegalStateException("verify: empty answer");
        }
        if (distractors.stream().anyMatch(d -> d.equalsIgnoreCase(draft.answer()))) {
            throw new IllegalStateException("verify: a distractor duplicates the correct answer");
        }
    }
}
