package com.engram.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI text-embedding-3-small, 1536 dimensions, input-only billing.
 * Direct HTTP — consistent with the spike's ClaudeClient pattern; no LangChain4j.
 *
 * Auth: reads OPENAI_API_KEY from env. Never hard-code a key.
 */
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingProvider.class);

    private static final String ENDPOINT  = "https://api.openai.com/v1/embeddings";
    private static final String MODEL_ID  = "text-embedding-3-small";
    private static final int    DIMENSION = 1536;
    // OpenAI hard cap per input string; truncate defensively (concept snippets are short).
    private static final int    MAX_CHARS = 8191 * 4; // ~4 chars/token upper bound

    private final HttpClient   http;
    private final ObjectMapper mapper;
    private final String       apiKey;

    public OpenAiEmbeddingProvider() {
        this.http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.mapper = new ObjectMapper();
        this.apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        ObjectNode body = mapper.createObjectNode();
        body.put("model", MODEL_ID);
        ArrayNode input = body.putArray("input");
        for (String t : texts) {
            input.add(truncate(t));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("OpenAI embeddings error " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode data = root.path("data");

            // data is sorted by index field — collect in order.
            List<float[]> result = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) result.add(null);
            for (JsonNode item : data) {
                int idx = item.path("index").asInt();
                JsonNode embNode = item.path("embedding");
                float[] vec = new float[DIMENSION];
                for (int j = 0; j < DIMENSION; j++) {
                    vec[j] = (float) embNode.get(j).asDouble();
                }
                result.set(idx, vec);
            }

            JsonNode usage = root.path("usage");
            log.info("OpenAI embeddings: {} texts, {} prompt tokens", texts.size(),
                    usage.path("prompt_tokens").asInt(0));

            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI embeddings call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimension() { return DIMENSION; }

    @Override
    public String modelId() { return MODEL_ID; }

    private String truncate(String text) {
        if (text.length() <= MAX_CHARS) return text;
        log.warn("Embedding input truncated from {} to {} chars", text.length(), MAX_CHARS);
        return text.substring(0, MAX_CHARS);
    }
}
