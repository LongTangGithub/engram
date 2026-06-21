package com.engram.activation;

import com.engram.concept.ConceptCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Fixed pipeline: Professor (cheap) → Distractor (expensive) → repair → optional single retry.
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
        verifyDraft(draft);

        // Step 2: build grounding note for Distractor
        String groundingNote = buildDistractorGrounding(concept, neighbors);
        if (neighbors.isEmpty()) {
            log.info("cold vault — no neighbors for concept " + concept.conceptId() + "; distractor falls back to sourceSpan only");
        }

        // Step 3: write distractors (expensive)
        Distractor.Result distractorResult = distractor.write(draft.question(), draft.answer(), groundingNote);

        // Mutable cost accumulators — grow if repair retry is needed
        int totalInput = draft.usage().inputTokens() + distractorResult.usage().inputTokens();
        int totalOutput = draft.usage().outputTokens() + distractorResult.usage().outputTokens();
        long totalCost = ModelTier.CHEAP.toMicros(draft.usage().inputTokens(), draft.usage().outputTokens())
                + ModelTier.EXPENSIVE.toMicros(distractorResult.usage().inputTokens(), distractorResult.usage().outputTokens());

        // Step 4: filter answer-colliding distractors; retry once if <3 valid remain
        List<String> valid = filterValid(distractorResult.distractors(), draft.answer());

        if (valid.size() < 3) {
            log.info("verify: only " + valid.size() + " clean distractors — retrying once");
            Distractor.Result retry = distractor.write(draft.question(), draft.answer(), groundingNote);
            totalInput += retry.usage().inputTokens();
            totalOutput += retry.usage().outputTokens();
            totalCost += ModelTier.EXPENSIVE.toMicros(retry.usage().inputTokens(), retry.usage().outputTokens());

            // Merge survivors from first call + unique valid from retry
            List<String> merged = new ArrayList<>(valid);
            for (String d : filterValid(retry.distractors(), draft.answer())) {
                if (merged.stream().noneMatch(m -> m.equalsIgnoreCase(d))) {
                    merged.add(d);
                }
            }
            if (merged.size() < 3) {
                throw new ActivationGenerationException(
                        "distractor repair: only " + merged.size() + " valid after retry (need 3)");
            }
            valid = merged.subList(0, 3);
        }

        return new GenerationResult(
                draft.question(),
                draft.answer(),
                valid,
                ModelTier.EXPENSIVE.modelId,
                totalInput,
                totalOutput,
                totalCost);
    }

    private static List<String> filterValid(List<String> distractors, String correctAnswer) {
        return distractors.stream()
                .filter(d -> !d.equalsIgnoreCase(correctAnswer))
                .distinct()
                .collect(Collectors.toList());
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

    private static void verifyDraft(Professor.Draft draft) {
        if (draft.question() == null || draft.question().isBlank()) {
            throw new IllegalStateException("verify: empty question");
        }
        if (draft.answer() == null || draft.answer().isBlank()) {
            throw new IllegalStateException("verify: empty answer");
        }
    }
}
