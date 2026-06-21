package com.engram.activation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

// Writes 3 plausible-but-wrong distractors. EXPENSIVE tier — distractor quality is the moat.
// groundingNote = concept's sourceSpan + optional vault neighbor context.
public class Distractor {

    static final String PROMPT_VERSION = "distractor-v1";

    static final String SYSTEM = """
            You write 3 plausible but INCORRECT answer options for a recall question.
            Each must be tempting to someone with shallow understanding, yet genuinely wrong.
            Avoid joke answers and obvious throwaways. Do not duplicate the correct answer.
            Return ONLY a JSON array of 3 strings (no prose, no fences).
            """;

    private final ClaudeClient claude;
    private final ObjectMapper mapper = new ObjectMapper();

    public Distractor(ClaudeClient claude) {
        this.claude = claude;
    }

    public record Result(List<String> distractors, LlmResponse usage) {}

    public Result write(String question, String correctAnswer, String groundingNote) {
        String userText = """
                QUESTION: %s
                CORRECT ANSWER: %s

                CONTEXT NOTE:
                %s
                """.formatted(question, correctAnswer, groundingNote);

        LlmResponse resp = claude.complete(ModelTier.EXPENSIVE, SYSTEM, userText, 600);

        List<String> distractors = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(resp.text().replaceAll("(?s)```(?:json)?", "").trim());
            for (JsonNode node : arr) {
                distractors.add(node.asText());
            }
        } catch (Exception e) {
            throw new RuntimeException("Distractor did not return parseable JSON:\n" + resp.text(), e);
        }
        return new Result(distractors, resp);
    }
}
