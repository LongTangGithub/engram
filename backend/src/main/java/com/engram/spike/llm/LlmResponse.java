package com.engram.spike.llm;

/**
 * One LLM call's result. The token counts are the whole reason the spike uses a
 * thin direct client instead of a framework: cost measurement (ENG-1) needs
 * usage.input_tokens / usage.output_tokens exposed cleanly.
 */
public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        String modelId
) {}
