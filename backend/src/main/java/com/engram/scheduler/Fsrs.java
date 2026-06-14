package com.engram.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * FSRS-6 memory-decay engine.
 *
 * Ported from py-fsrs 6.3.1 (https://github.com/open-spaced-repetition/py-fsrs).
 * Default weights copied verbatim from DEFAULT_PARAMETERS in that release.
 *
 * This class implements Review-state math only (no learning-step state machine).
 * Elapsed days between reviews drives the retrievability and stability formulas.
 * Short-term stability (same-day re-reviews within learning steps) is out of scope;
 * it belongs in the scheduling layer, not the memory-decay engine.
 */
public class Fsrs implements RetrievabilityEngine {

    // ── Default weights — py-fsrs 6.3.1, FSRS-6 ─────────────────────────────
    // w[0..3]   : initial stability per rating (Again, Hard, Good, Easy)
    // w[4..5]   : initial difficulty base and rating factor
    // w[6]      : delta difficulty factor
    // w[7]      : mean reversion weight (toward Easy initial difficulty)
    // w[8..10]  : recall stability: e^w8 factor, S power, R factor
    // w[11..14] : forget stability: factor, D power, S+1 power, R factor
    // w[15]     : hard penalty on recall stability
    // w[16]     : easy bonus on recall stability
    // w[17..19] : short-term stability (not used here — learning-step domain)
    // w[20]     : DECAY base parameter (DECAY = -w[20])

    private static final double[] W = {
        0.212,   // w[0]
        1.2931,  // w[1]
        2.3065,  // w[2]
        8.2956,  // w[3]
        6.4133,  // w[4]
        0.8334,  // w[5]
        3.0194,  // w[6]
        0.001,   // w[7]
        1.8722,  // w[8]
        0.1666,  // w[9]
        0.796,   // w[10]
        1.4835,  // w[11]
        0.0614,  // w[12]
        0.2629,  // w[13]
        1.6483,  // w[14]
        0.6014,  // w[15]
        1.8729,  // w[16]
        0.5425,  // w[17] — short-term (unused in Review-state engine)
        0.0912,  // w[18] — short-term (unused)
        0.0658,  // w[19] — short-term (unused)
        0.1542,  // w[20]
    };

    // DECAY = -w[20]; FACTOR = 0.9^(1/DECAY) - 1
    // These reproduce the exact values from py-fsrs Scheduler.__init__
    private static final double DECAY  = -W[20];                           // -0.1542
    private static final double FACTOR = Math.pow(0.9, 1.0 / DECAY) - 1;  // 0.9803464944134797

    private static final double STABILITY_MIN  = 0.001;
    private static final double DIFFICULTY_MIN = 1.0;
    private static final double DIFFICULTY_MAX = 10.0;

    @Override
    public String version() {
        return "FSRS-6";
    }

    // ── RetrievabilityEngine ─────────────────────────────────────────────────

    @Override
    public double retrievability(FsrsState state, Instant now) {
        if (state == null || state.lastReviewedAt() == null) return 0.0;
        double elapsed = elapsedDays(state.lastReviewedAt(), now);
        return retrievabilityFormula(elapsed, state.stability());
    }

    @Override
    public FsrsState review(FsrsState prior, int rating, Instant reviewedAt) {
        validateRating(rating);
        if (prior == null) {
            return new FsrsState(
                    clampStability(initialStability(rating)),
                    clampDifficulty(initialDifficulty(rating)),
                    reviewedAt);
        }
        double elapsed = elapsedDays(prior.lastReviewedAt(), reviewedAt);
        double r = retrievabilityFormula(elapsed, prior.stability());
        double newS = (rating == 1)
                ? nextForgetStability(prior.difficulty(), prior.stability(), r)
                : nextRecallStability(prior.difficulty(), prior.stability(), r, rating);
        double newD = nextDifficulty(prior.difficulty(), rating);
        return new FsrsState(clampStability(newS), clampDifficulty(newD), reviewedAt);
    }

    // ── Core formulas (package-visible for tests) ────────────────────────────

    double retrievabilityFormula(double elapsedDays, double stability) {
        return Math.pow(1 + FACTOR * elapsedDays / stability, DECAY);
    }

    double initialStability(int rating) {
        // w[0]=Again w[1]=Hard w[2]=Good w[3]=Easy
        return W[rating - 1];
    }

    double initialDifficulty(int rating) {
        return W[4] - Math.exp(W[5] * (rating - 1)) + 1;
    }

    double nextDifficulty(double d, int rating) {
        // mean reversion toward Easy initial difficulty (unclamped)
        double easyInitialD = W[4] - Math.exp(W[5] * 3) + 1;  // rating=4 → (rating-1)=3
        double deltaD = -(W[6] * (rating - 3));
        double linearDamped = (10.0 - d) * deltaD / 9.0;
        double arg2 = d + linearDamped;
        return W[7] * easyInitialD + (1 - W[7]) * arg2;
    }

    double nextRecallStability(double d, double s, double r, int rating) {
        double hardPenalty = (rating == 2) ? W[15] : 1.0;
        double easyBonus   = (rating == 4) ? W[16] : 1.0;
        return s * (1 + Math.exp(W[8]) * (11 - d)
                * Math.pow(s, -W[9])
                * (Math.exp((1 - r) * W[10]) - 1)
                * hardPenalty * easyBonus);
    }

    double nextForgetStability(double d, double s, double r) {
        double longTerm  = W[11] * Math.pow(d, -W[12])
                * (Math.pow(s + 1, W[13]) - 1)
                * Math.exp((1 - r) * W[14]);
        double shortTerm = s / Math.exp(W[17] * W[18]);
        return Math.min(longTerm, shortTerm);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static double elapsedDays(Instant from, Instant to) {
        return (double) ChronoUnit.SECONDS.between(from, to) / 86400.0;
    }

    private static double clampStability(double s) {
        return Math.max(s, STABILITY_MIN);
    }

    private static double clampDifficulty(double d) {
        return Math.max(DIFFICULTY_MIN, Math.min(DIFFICULTY_MAX, d));
    }

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 4) {
            throw new IllegalArgumentException("rating must be 1-4, got: " + rating);
        }
    }
}