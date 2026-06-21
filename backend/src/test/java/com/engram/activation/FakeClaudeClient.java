package com.engram.activation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Test double for ClaudeClient. Captures every call so tests can assert on prompts passed in.
// Returns valid canned JSON per tier — tests verify inputs, not outputs.
public class FakeClaudeClient implements ClaudeClient {

    public record Call(ModelTier tier, String system, String userText) {}

    private final List<Call> calls = new ArrayList<>();

    @Override
    public LlmResponse complete(ModelTier tier, String system, String userText, int maxTokens) {
        calls.add(new Call(tier, system, userText));
        String text = switch (tier) {
            case CHEAP -> """
                    {"question": "What does spaced repetition optimize?", "answer": "Long-term memory retention"}
                    """;
            case EXPENSIVE -> """
                    ["Short-term memorization", "Random guess intervals", "Passive re-reading"]
                    """;
        };
        return new LlmResponse(text, 100, 50, tier.modelId);
    }

    public List<Call> calls() {
        return Collections.unmodifiableList(calls);
    }

    public int callCount() {
        return calls.size();
    }

    public void reset() {
        calls.clear();
    }
}
