package com.engram.concept;

import com.engram.ingest.IngestedDocument;
import com.engram.spike.llm.ClaudeClient;
import com.engram.spike.llm.CostLog;
import com.engram.spike.llm.ModelTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Real Extractor impl that calls Claude Haiku (CHEAP tier).
 * Wired for production; NOT instantiated in tests — tests use a mock Extractor.
 * LangChain4j replaces this at ENG-8.
 */
public class ClaudeExtractor implements Extractor {

    private static final String SYSTEM = """
            You extract atomic, recallable concepts from a note for spaced-repetition review.
            Return ONLY a JSON array (no prose, no markdown fences). Each element:
            {"title": "...", "topicTag": "...", "sourceSpan": "the exact sentence(s) the concept came from"}
            A good concept is one self-contained idea worth remembering. Aim for 3-8 concepts.
            """;

    private final ClaudeClient claude;
    private final CostLog costLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeExtractor(ClaudeClient claude, CostLog costLog) {
        this.claude = claude;
        this.costLog = costLog;
    }

    @Override
    public List<ExtractedConcept> extract(IngestedDocument doc) {
        var resp = claude.complete(ModelTier.CHEAP, SYSTEM, doc.content(), 1500);
        costLog.record("extract", ModelTier.CHEAP, resp);

        List<ExtractedConcept> concepts = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(stripFences(resp.text()));
            for (JsonNode node : arr) {
                concepts.add(new ExtractedConcept(
                        node.path("title").asText(),
                        node.path("topicTag").asText(),
                        node.path("sourceSpan").asText()));
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Extractor returned unparseable JSON. Raw output:\n" + resp.text(), e);
        }
        return concepts;
    }

    private static String stripFences(String s) {
        return s.replaceAll("(?s)```(?:json)?", "").trim();
    }
}
