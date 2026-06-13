package com.engram.spike.generation;

import com.engram.spike.concept.ConceptCandidate;
import com.engram.spike.llm.ClaudeClient;
import com.engram.spike.llm.CostLog;
import com.engram.spike.llm.LlmResponse;
import com.engram.spike.llm.ModelTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * The Professor — drafts a question and an accurate answer for a concept,
 * grounded in the source note. Runs on {@link ModelTier#CHEAP}: the expensive
 * tier is reserved for the Distractor alone.
 */
@Component
public class Professor {

    private static final String SYSTEM = """
            You write one high-quality recall question and its correct answer for a concept,
            grounded ONLY in the provided source note. Do not invent facts beyond the note.
            Return ONLY JSON (no prose, no fences): {"question": "...", "answer": "..."}
            The question should demand active recall, not recognition.
            """;

    private final ClaudeClient claude;
    private final CostLog costLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public Professor(ClaudeClient claude, CostLog costLog) {
        this.claude = claude;
        this.costLog = costLog;
    }

    public record Draft(String question, String answer) {}

    public Draft draft(ConceptCandidate concept, String groundingNote) {
        String user = """
                CONCEPT: %s (topic: %s)
                SOURCE SPAN: %s

                FULL NOTE (grounding):
                %s
                """.formatted(concept.title(), concept.topicTag(), concept.sourceSpan(), groundingNote);

        LlmResponse resp = claude.complete(ModelTier.CHEAP, SYSTEM, user, 800);
        costLog.record("professor", ModelTier.CHEAP, resp);

        try {
            JsonNode node = mapper.readTree(resp.text().replaceAll("(?s)```(?:json)?", "").trim());
            return new Draft(node.path("question").asText(), node.path("answer").asText());
        } catch (Exception e) {
            throw new RuntimeException("Professor did not return parseable JSON:\n" + resp.text(), e);
        }
    }
}
