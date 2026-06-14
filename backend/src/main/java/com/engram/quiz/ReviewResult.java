package com.engram.quiz;

import java.time.Instant;

public record ReviewResult(
        double retrievabilityNow,
        Instant dueAt,
        String lifecycleState
) {}
