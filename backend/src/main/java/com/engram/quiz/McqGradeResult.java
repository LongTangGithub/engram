package com.engram.quiz;

import java.time.Instant;

/**
 * Result of an auto-graded MCQ review.
 *
 * Carries the scheduler result (same fields as {@link ReviewResult}) plus the server-decided
 * outcome: whether the pick was correct and what the correct answer was. The correct answer is
 * returned ONLY here — after the user has committed a pick — never in the pre-commit card payload
 * (Law 1: the answer is not shipped to the client before they commit).
 */
public record McqGradeResult(
        double  retrievabilityNow,
        Instant dueAt,
        String  lifecycleState,
        boolean isCorrect,
        String  correctAnswer
) {}
