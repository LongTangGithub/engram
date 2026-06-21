package com.engram.activation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Test double for ClaudeClient. Captures every call so tests can assert on prompts passed in.
// Supports per-tier response queues for A2 repair tests (enqueueResponses sets ordered replies).
// Default responses used when queue is empty.
public class FakeClaudeClient implements ClaudeClient {

    public record Call(ModelTier tier, String system, String userText) {}

    private final List<Call> calls = new ArrayList<>();
    private final Map<ModelTier, Deque<String>> responseQueues = new EnumMap<>(ModelTier.class);

    @Override
    public LlmResponse complete(ModelTier tier, String system, String userText, int maxTokens) {
        calls.add(new Call(tier, system, userText));
        Deque<String> queue = responseQueues.get(tier);
        String text;
        if (queue != null && !queue.isEmpty()) {
            text = queue.poll();
        } else {
            text = switch (tier) {
                case CHEAP -> """
                        {"question": "What does spaced repetition optimize?", "answer": "Long-term memory retention"}
                        """;
                case EXPENSIVE -> """
                        ["Short-term memorization", "Random guess intervals", "Passive re-reading"]
                        """;
            };
        }
        return new LlmResponse(text, 100, 50, tier.modelId);
    }

    // Set ordered responses for a specific tier; drained FIFO, fallback to default when exhausted.
    public void enqueueResponses(ModelTier tier, String... responses) {
        responseQueues.put(tier, new ArrayDeque<>(Arrays.asList(responses)));
    }

    public List<Call> calls() {
        return Collections.unmodifiableList(calls);
    }

    public int callCount() {
        return calls.size();
    }

    public void reset() {
        calls.clear();
        responseQueues.clear();
    }
}
