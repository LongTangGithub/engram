package com.engram.activation;

// Distractor is the ONLY job on EXPENSIVE — that's the margin lever.
// All other generation (Professor, Extractor, cloze) runs on CHEAP.
public enum ModelTier {

    CHEAP("claude-haiku-4-5-20251001", 1.00, 5.00),
    EXPENSIVE("claude-sonnet-4-6", 3.00, 15.00);

    public final String modelId;
    public final double inputPricePerMTok;   // USD per 1,000,000 input tokens
    public final double outputPricePerMTok;  // USD per 1,000,000 output tokens

    ModelTier(String modelId, double inputPricePerMTok, double outputPricePerMTok) {
        this.modelId = modelId;
        this.inputPricePerMTok = inputPricePerMTok;
        this.outputPricePerMTok = outputPricePerMTok;
    }

    // cost_micros: (tokens / 1_000_000 * pricePerMTok) * 1_000_000 = tokens * pricePerMTok
    public long toMicros(int inputTokens, int outputTokens) {
        return Math.round(inputTokens * inputPricePerMTok + outputTokens * outputPricePerMTok);
    }
}
