package com.engram.spike.generation;

import com.engram.spike.concept.ActivatedCard;
import com.engram.spike.concept.ConceptCandidate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The deterministic generation pipeline (locked shape):
 *   Extract → ground (RAG) → draft (Professor) → distract (Distractor) → verify.
 *
 * <p>"Deterministic" means a fixed sequence with structured handoffs — no
 * self-replanning agent loops, no fine-tuning. The orchestrator owns the order;
 * each agent owns its one job.
 *
 * <p>Spike scope: grounding = passing the source note directly (no pgvector yet),
 * and "verify" is a light sanity check. ENG-8 adds real RAG grounding and
 * vault-sourced distractors.
 */
@Component
public class GenerationOrchestrator {

    private final Professor professor;
    private final Distractor distractor;

    public GenerationOrchestrator(Professor professor, Distractor distractor) {
        this.professor = professor;
        this.distractor = distractor;
    }

    /** Activate one candidate into a full card (the candidate -> activated transition). */
    public ActivatedCard activate(ConceptCandidate candidate, String groundingNote) {
        // draft (cheap)
        Professor.Draft draft = professor.draft(candidate, groundingNote);

        // distract (expensive — the only expensive call)
        List<String> distractors = distractor.write(draft.question(), draft.answer(), groundingNote);

        // verify (light sanity check; ENG-8 makes this rigorous)
        verify(draft, distractors);

        return new ActivatedCard(candidate.title(), draft.question(), draft.answer(), distractors);
    }

    private void verify(Professor.Draft draft, List<String> distractors) {
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
