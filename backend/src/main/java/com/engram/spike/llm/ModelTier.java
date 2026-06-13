package com.engram.spike.llm;

/**
 * The model-tiering decision (locked): the expensive model is reserved for the
 * Distractor ONLY, because that is the one job where quality is the moat.
 * Everything else — Extractor, candidate pass, cloze, grading, the Professor —
 * runs on the cheap tier. This split is the single biggest margin lever.
 *
 * <p>IMPORTANT: model IDs and prices change. Both fields below are placeholders
 * set to 0.0 on purpose so the spike cannot silently report a fake cost. Set
 * them to the CURRENT published per-million-token prices (anthropic.com/pricing)
 * and confirm the model IDs before trusting the $/activation output.
 */
public enum ModelTier {

    /** Cheap/fast tier — Extractor, candidate pass, cloze, grading, Professor. */
    CHEAP("claude-haiku-4-5-20251001", 0.0, 0.0),

    /** Expensive tier — Distractor only. */
    EXPENSIVE("claude-sonnet-4-6", 0.0, 0.0);

    public final String modelId;
    public final double inputPricePerMTok;   // USD per 1,000,000 input tokens  — TODO: set from current pricing
    public final double outputPricePerMTok;  // USD per 1,000,000 output tokens — TODO: set from current pricing

    ModelTier(String modelId, double inputPricePerMTok, double outputPricePerMTok) {
        this.modelId = modelId;
        this.inputPricePerMTok = inputPricePerMTok;
        this.outputPricePerMTok = outputPricePerMTok;
    }

    boolean pricesUnset() {
        return inputPricePerMTok == 0.0 && outputPricePerMTok == 0.0;
    }
}
