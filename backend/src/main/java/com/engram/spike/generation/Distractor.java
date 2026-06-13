package com.engram.spike.generation;

import com.engram.spike.llm.ClaudeClient;
import com.engram.spike.llm.CostLog;
import com.engram.spike.llm.LlmResponse;
import com.engram.spike.llm.ModelTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Distractor — writes plausible, tricky wrong answers. This is the ONE job
 * that runs on {@link ModelTier#EXPENSIVE}, because distractor quality is the moat:
 * wrong answers that are convincing but genuinely wrong are what make MCQ valuable.
 *
 * <p>Spike scope: distractors are drawn from the concept + note context. TRUE
 * vault-sourced distractors (pulled from neighboring concepts in the user's own
 * vault via pgvector) require the embedding store — that arrives at ENG-4/ENG-8.
 * Judging spike quality, note whether these feel plausible-but-wrong vs. obviously
 * off; that gap is exactly what vault-sourcing later closes.
 */
@Component
public class Distractor {

    private static final String SYSTEM = """
            You write 3 plausible but INCORRECT answer options for a recall question.
            Each must be tempting to someone with shallow understanding, yet genuinely wrong.
            Avoid joke answers and obvious throwaways. Do not duplicate the correct answer.
            Return ONLY a JSON array of 3 strings (no prose, no fences).
            """;

    private final ClaudeClient claude;
    private final CostLog costLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public Distractor(ClaudeClient claude, CostLog costLog) {
        this.claude = claude;
        this.costLog = costLog;
    }

    public List<String> write(String question, String correctAnswer, String groundingNote) {
        String user = """
                QUESTION: %s
                CORRECT ANSWER: %s

                CONTEXT NOTE:
                %s
                """.formatted(question, correctAnswer, groundingNote);

        LlmResponse resp = claude.complete(ModelTier.EXPENSIVE, SYSTEM, user, 600);
        costLog.record("distractor", ModelTier.EXPENSIVE, resp);

        List<String> distractors = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(resp.text().replaceAll("(?s)```(?:json)?", "").trim());
            for (JsonNode node : arr) {
                distractors.add(node.asText());
            }
        } catch (Exception e) {
            throw new RuntimeException("Distractor did not return parseable JSON:\n" + resp.text(), e);
        }
        return distractors;
    }
}
