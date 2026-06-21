package com.engram.activation;

import java.util.List;

public record GenerationResult(
        String question,
        String answer,
        List<String> distractors,
        String generationModel,
        int totalInputTokens,
        int totalOutputTokens,
        long costMicros
) {}
