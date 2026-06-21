package com.engram.api;

import com.engram.activation.ActivatedCard;
import com.engram.activation.ActivatedCardRepository;
import com.engram.quiz.ClozeCard;
import com.engram.quiz.ReviewResult;
import com.engram.quiz.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final ActivatedCardRepository cardRepo;

    public ReviewController(ReviewService reviewService, ActivatedCardRepository cardRepo) {
        this.reviewService = reviewService;
        this.cardRepo = cardRepo;
    }

    /**
     * GET /api/review/next?userId={uuid}
     * Returns next card to review. If an activated_card exists for the due concept,
     * serves an MCQ card (cardType=mcq); otherwise serves cloze (cardType=cloze).
     * Card type selection policy is ENG-9.
     */
    @GetMapping("/next")
    public ResponseEntity<NextCardResponse> next(@RequestParam UUID userId) {
        return reviewService.nextCard(userId)
                .map(clozeCard -> {
                    Optional<ActivatedCard> activated = cardRepo.findByConceptId(clozeCard.conceptId());
                    NextCardResponse resp = activated.isPresent()
                            ? NextCardResponse.fromMcq(activated.get())
                            : NextCardResponse.fromCloze(clozeCard);
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST /api/review/submit
     * Records a self-graded review and returns the updated scheduler state.
     */
    @PostMapping("/submit")
    public ReviewResultResponse submit(@RequestBody SubmitRequest req) {
        ReviewResult result = reviewService.submitReview(
                req.userId(), req.conceptId(), req.rating(),
                req.clientEventId(), req.reviewedAt(),
                req.format() != null ? req.format() : "cloze");
        return ReviewResultResponse.from(result);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record NextCardResponse(
            String cardType,   // "cloze" | "mcq"
            String conceptId,
            String prompt,     // cloze only (null for mcq)
            String answer,     // cloze only (null for mcq)
            String cardId,     // mcq only (null for cloze)
            String question,   // mcq only (null for cloze)
            List<String> options // mcq only: shuffled correctAnswer + distractors, no correct flag
    ) {
        static NextCardResponse fromCloze(ClozeCard card) {
            return new NextCardResponse("cloze", card.conceptId().toString(),
                    card.prompt(), card.answer(), null, null, null);
        }

        static NextCardResponse fromMcq(ActivatedCard card) {
            List<String> opts = new ArrayList<>(card.distractors());
            opts.add(card.correctAnswer());
            Collections.shuffle(opts);
            return new NextCardResponse("mcq", card.conceptId().toString(),
                    null, null, card.cardId().toString(), card.question(), opts);
        }
    }

    public record SubmitRequest(
            UUID userId,
            UUID conceptId,
            int rating,           // 1=Again 2=Hard 3=Good 4=Easy
            String clientEventId, // caller-generated UUID string for idempotency
            Instant reviewedAt,
            String format         // "cloze" | "mcq"; null → defaults to "cloze"
    ) {}

    public record ReviewResultResponse(double retrievabilityNow, Instant dueAt, String lifecycleState) {
        static ReviewResultResponse from(ReviewResult r) {
            return new ReviewResultResponse(r.retrievabilityNow(), r.dueAt(), r.lifecycleState());
        }
    }
}
