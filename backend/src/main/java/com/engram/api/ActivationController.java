package com.engram.api;

import com.engram.activation.ActivatedCard;
import com.engram.activation.ActivatedCardRepository;
import com.engram.activation.ActivationGenerationException;
import com.engram.activation.ActivationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activate")
public class ActivationController {

    private final ActivationService activationService;
    private final ActivatedCardRepository cardRepo;

    public ActivationController(ActivationService activationService, ActivatedCardRepository cardRepo) {
        this.activationService = activationService;
        this.cardRepo = cardRepo;
    }

    /**
     * POST /api/activate
     * Lazily generates (or returns cached) an MCQ card for the given concept.
     * Response intentionally omits correctAnswer — fetch via GET /{conceptId}/reveal
     * after the user clicks Reveal (Law 1: answer never in DOM before Reveal).
     */
    @PostMapping
    public ResponseEntity<?> activate(@RequestBody ActivateRequest req) {
        if (req.conceptId() == null) {
            return ResponseEntity.badRequest().body("conceptId is required");
        }
        String idempotencyKey = req.idempotencyKey() != null
                ? req.idempotencyKey()
                : "activate:" + req.conceptId();
        try {
            ActivatedCard card = activationService.activate(req.userId(), req.conceptId(), idempotencyKey);
            return ResponseEntity.ok(ActivateResponse.from(card));
        } catch (ActivationGenerationException e) {
            return ResponseEntity.unprocessableEntity().body(e.getMessage());
        }
    }

    /**
     * GET /api/activate/{conceptId}/reveal
     * Returns the correct answer string. Called only after user clicks Reveal.
     * Frontend highlights the option whose text equals this value.
     */
    @GetMapping("/{conceptId}/reveal")
    public ResponseEntity<RevealResponse> reveal(@PathVariable UUID conceptId) {
        return cardRepo.findByConceptId(conceptId)
                .map(card -> ResponseEntity.ok(new RevealResponse(card.correctAnswer())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record ActivateRequest(UUID userId, UUID conceptId, String idempotencyKey) {}

    public record ActivateResponse(String cardId, String conceptId, String question, List<String> options) {
        static ActivateResponse from(ActivatedCard card) {
            List<String> opts = new ArrayList<>(card.distractors());
            opts.add(card.correctAnswer());
            Collections.shuffle(opts);
            return new ActivateResponse(
                    card.cardId().toString(),
                    card.conceptId().toString(),
                    card.question(),
                    opts);
        }
    }

    public record RevealResponse(String correctAnswer) {}
}
