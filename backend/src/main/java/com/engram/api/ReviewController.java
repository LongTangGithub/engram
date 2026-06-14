package com.engram.api;

import com.engram.quiz.ClozeCard;
import com.engram.quiz.ReviewResult;
import com.engram.quiz.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * GET /api/review/next?userId={uuid}
     * Returns the next card to review. 204 No Content if no candidates exist yet.
     */
    @GetMapping("/next")
    public ResponseEntity<ClozeCardResponse> next(@RequestParam UUID userId) {
        return reviewService.nextCard(userId)
                .map(card -> ResponseEntity.ok(ClozeCardResponse.from(card)))
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
                req.clientEventId(), req.reviewedAt());
        return ReviewResultResponse.from(result);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record ClozeCardResponse(String conceptId, String prompt, String answer) {
        static ClozeCardResponse from(ClozeCard card) {
            return new ClozeCardResponse(card.conceptId().toString(), card.prompt(), card.answer());
        }
    }

    public record SubmitRequest(
            UUID userId,
            UUID conceptId,
            int rating,           // 1=Again 2=Hard 3=Good 4=Easy
            String clientEventId, // caller-generated UUID string for idempotency
            Instant reviewedAt
    ) {}

    public record ReviewResultResponse(double retrievabilityNow, Instant dueAt, String lifecycleState) {
        static ReviewResultResponse from(ReviewResult r) {
            return new ReviewResultResponse(r.retrievabilityNow(), r.dueAt(), r.lifecycleState());
        }
    }
}
