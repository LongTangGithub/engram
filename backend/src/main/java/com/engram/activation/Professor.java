package com.engram.activation;

import com.engram.concept.ConceptCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Drafts one question + correct answer for a concept, grounded ONLY in its sourceSpan.
// Runs on CHEAP — expensive tier is reserved for Distractor alone.
public class Professor {

    static final String SYSTEM = """
            You write one high-quality recall question and its correct answer for a concept,
            grounded ONLY in the provided source note. Do not invent facts beyond the note.
            Return ONLY JSON (no prose, no fences): {"question": "...", "answer": "..."}
            The question should demand active recall, not recognition.
            """;

    static final String PROMPT_VERSION = "professor-v1";

    private final ClaudeClient claude;
    private final ObjectMapper mapper = new ObjectMapper();

    public Professor(ClaudeClient claude) {
        this.claude = claude;
    }

    public record Draft(String question, String answer, LlmResponse usage) {}

    public Draft draft(ConceptCandidate concept) {
        String userText = """
                CONCEPT: %s (topic: %s)
                SOURCE SPAN: %s
                """.formatted(concept.title(), concept.topicTag(), concept.sourceSpan());

        LlmResponse resp = claude.complete(ModelTier.CHEAP, SYSTEM, userText, 800);

        try {
            JsonNode node = mapper.readTree(resp.text().replaceAll("(?s)```(?:json)?", "").trim());
            return new Draft(node.path("question").asText(), node.path("answer").asText(), resp);
        } catch (Exception e) {
            throw new RuntimeException("Professor did not return parseable JSON:\n" + resp.text(), e);
        }
    }
}
