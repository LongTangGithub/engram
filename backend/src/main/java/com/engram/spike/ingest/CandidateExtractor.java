package com.engram.spike.ingest;

import com.engram.spike.concept.ConceptCandidate;
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
 * Cheap-tier extraction (the Extractor). Pulls concept candidates from a note.
 * Runs on {@link ModelTier#CHEAP} per the model-tiering decision. Output is
 * structured JSON — the deterministic-pipeline contract.
 *
 * <p>Spike scope: extracts candidates from a single note. Production (ENG-4)
 * also chunks + embeds and persists candidates as unseeded seeds.
 */
@Component
public class CandidateExtractor {

    private static final String SYSTEM = """
            You extract atomic, recallable concepts from a note for spaced-repetition review.
            Return ONLY a JSON array (no prose, no markdown fences). Each element:
            {"title": "...", "topicTag": "...", "sourceSpan": "the exact sentence(s) the concept came from"}
            A good concept is one self-contained idea worth remembering. Aim for 3-8 concepts.
            """;

    private final ClaudeClient claude;
    private final CostLog costLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public CandidateExtractor(ClaudeClient claude, CostLog costLog) {
        this.claude = claude;
        this.costLog = costLog;
    }

    public List<ConceptCandidate> extract(String noteText) {
        LlmResponse resp = claude.complete(ModelTier.CHEAP, SYSTEM, noteText, 1500);
        costLog.record("extract", ModelTier.CHEAP, resp);

        List<ConceptCandidate> candidates = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(stripFences(resp.text()));
            for (JsonNode node : arr) {
                candidates.add(new ConceptCandidate(
                        node.path("title").asText(),
                        node.path("topicTag").asText(),
                        node.path("sourceSpan").asText()));
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Extractor did not return parseable JSON. Raw output:\n" + resp.text(), e);
        }
        return candidates;
    }

    /** Defensive: strip accidental ```json fences if the model adds them. */
    private static String stripFences(String s) {
        return s.replaceAll("(?s)```(?:json)?", "").trim();
    }
}
