package com.engram.activation;

public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        String modelId
) {}
