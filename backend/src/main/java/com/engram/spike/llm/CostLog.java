package com.engram.spike.llm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The cost-measurement seam — the entire point of ENG-1.
 *
 * <p>Every LLM call in the activation pipeline records its stage, tier, and token
 * usage here. {@link #report()} then prints the per-stage breakdown and the total
 * cost of one activation — the number that validates (or kills) the pricing model.
 *
 * <p>This is request-scoped in the spike (one activation per run). In production
 * (ENG-11) the same idea becomes the metering counter.
 */
@Component
public class CostLog {

    public record Entry(String stage, ModelTier tier, int inputTokens, int outputTokens, double costUsd) {}

    private final List<Entry> entries = new ArrayList<>();

    public void record(String stage, ModelTier tier, LlmResponse response) {
        double cost = (response.inputTokens() / 1_000_000.0) * tier.inputPricePerMTok
                    + (response.outputTokens() / 1_000_000.0) * tier.outputPricePerMTok;
        entries.add(new Entry(stage, tier, response.inputTokens(), response.outputTokens(), cost));
    }

    public double totalCostUsd() {
        return entries.stream().mapToDouble(Entry::costUsd).sum();
    }

    public int totalInputTokens() {
        return entries.stream().mapToInt(Entry::inputTokens).sum();
    }

    public int totalOutputTokens() {
        return entries.stream().mapToInt(Entry::outputTokens).sum();
    }

    /** Human-readable report — the headline deliverable of the spike. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================ ACTIVATION COST REPORT (ENG-1) ================\n");
        sb.append(String.format("%-22s %-10s %10s %10s %12s%n",
                "stage", "tier", "in_tok", "out_tok", "cost_usd"));
        sb.append("---------------------------------------------------------------\n");
        for (Entry e : entries) {
            sb.append(String.format("%-22s %-10s %10d %10d %12.6f%n",
                    e.stage(), e.tier(), e.inputTokens(), e.outputTokens(), e.costUsd()));
        }
        sb.append("---------------------------------------------------------------\n");
        sb.append(String.format("%-22s %-10s %10d %10d %12.6f%n",
                "TOTAL / activation", "", totalInputTokens(), totalOutputTokens(), totalCostUsd()));
        sb.append("===============================================================\n");

        boolean anyPricesUnset = entries.stream().anyMatch(e -> e.tier().pricesUnset());
        if (anyPricesUnset) {
            sb.append("""
                    
                    !! WARNING: one or more ModelTier prices are still 0.0, so the cost above
                    !! is NOT real. Set ModelTier.inputPricePerMTok / outputPricePerMTok to the
                    !! current published prices (anthropic.com/pricing), then re-run.
                    """);
        }
        return sb.toString();
    }
}
