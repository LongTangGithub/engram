package com.engram.spike.scheduler;

import com.engram.spike.review.ReviewEvent;
import org.springframework.stereotype.Component;

/**
 * Minimal FSRS read-back — closes the spike loop by turning a review event into
 * an initial scheduler state (stability + retrievability) and a next-due estimate.
 *
 * <p>This is a deliberately tiny stand-in so ENG-1 can demonstrate
 * "review event -> FSRS -> retrievability" end to end. The REAL FSRS engine,
 * behind the {@code retrievability(concept, now)} interface, is ENG-5. Do not
 * grow this into the production scheduler — replace it.
 *
 * <p>The numbers below are illustrative initial-stability values, not tuned FSRS
 * parameters. They exist only to prove the wiring, not to schedule real reviews.
 */
@Component
public class FsrsReadback {

    public record SchedulerState(double stabilityDays, double retrievabilityNow, double dueInDays) {}

    /** Illustrative initial stability per first rating (days). Replace with real FSRS at ENG-5. */
    private static double initialStability(int fsrsRating) {
        return switch (fsrsRating) {
            case 1 -> 0.4;   // Again
            case 2 -> 1.2;   // Hard
            case 3 -> 3.0;   // Good
            case 4 -> 8.0;   // Easy
            default -> 1.0;
        };
    }

    public SchedulerState readBack(ReviewEvent event) {
        double stability = initialStability(event.fsrsRating());
        // Just-reviewed, so elapsed ~ 0 and retrievability ~ 1.0.
        double retrievabilityNow = 1.0;
        // FSRS schedules the next review near a target retrievability (~0.9);
        // for this stand-in, due ~= stability.
        double dueInDays = stability;
        return new SchedulerState(stability, retrievabilityNow, dueInDays);
    }
}
