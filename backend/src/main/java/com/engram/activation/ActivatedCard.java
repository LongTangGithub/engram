package com.engram.activation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivatedCard(
        UUID cardId,
        UUID conceptId,
        UUID userId,
        String question,
        String correctAnswer,
        List<String> distractors,
        String generationModel,
        String generationPromptVersion,
        int inputTokens,
        int outputTokens,
        long costMicros,
        String idempotencyKey,
        Instant createdAt
) {}
