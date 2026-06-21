package com.engram.activation;

import com.engram.concept.CandidateVectorRepository;
import com.engram.concept.ConceptCandidate;
import com.engram.concept.ConceptCandidateRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ActivationService {

    private static final int NEIGHBOR_K = 5;

    private final ConceptCandidateRepository conceptRepo;
    private final CandidateVectorRepository vectorRepo;
    private final ActivatedCardRepository cardRepo;
    private final GenerationOrchestrator orchestrator;

    public ActivationService(ConceptCandidateRepository conceptRepo,
                             CandidateVectorRepository vectorRepo,
                             ActivatedCardRepository cardRepo,
                             GenerationOrchestrator orchestrator) {
        this.conceptRepo = conceptRepo;
        this.vectorRepo = vectorRepo;
        this.cardRepo = cardRepo;
        this.orchestrator = orchestrator;
    }

    // generate-once: same conceptId → returns cached card, zero model calls on second invocation.
    public ActivatedCard activate(UUID userId, UUID conceptId, String idempotencyKey) {
        // Cache check — generate-once guarantee
        var existing = cardRepo.findByConceptId(conceptId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ConceptCandidate concept = conceptRepo.findById(conceptId)
                .orElseThrow(() -> new IllegalArgumentException("concept not found: " + conceptId));

        // Load vault neighbors for vault-sourced distractor grounding
        List<UUID> neighborIds = vectorRepo.findNearestNeighbors(userId, conceptId, NEIGHBOR_K);
        List<ConceptCandidate> neighbors = conceptRepo.findByIds(neighborIds);

        GenerationResult result = orchestrator.generate(concept, neighbors);

        ActivatedCard card = new ActivatedCard(
                UUID.randomUUID(),
                conceptId,
                userId,
                result.question(),
                result.answer(),
                result.distractors(),
                result.generationModel(),
                Professor.PROMPT_VERSION,
                result.totalInputTokens(),
                result.totalOutputTokens(),
                result.costMicros(),
                idempotencyKey,
                Instant.now());

        boolean saved = cardRepo.save(card);
        if (!saved) {
            // Concurrent race loser: another thread persisted first, return their version
            return cardRepo.findByConceptId(conceptId)
                    .orElseThrow(() -> new IllegalStateException("race condition: card disappeared after conflict"));
        }

        // Mark concept as activated (orthogonal to lifecycle_state/SEEDED)
        conceptRepo.setActivatedAt(conceptId, card.createdAt());

        return card;
    }
}
