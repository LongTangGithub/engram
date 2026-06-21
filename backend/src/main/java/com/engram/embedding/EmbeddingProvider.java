package com.engram.embedding;

import java.util.List;

/**
 * Swappable seam for text-embedding models.
 * Default impl: OpenAI text-embedding-3-small.
 * Same discipline as RetrievabilityEngine — callers depend on this interface, never the concrete class.
 */
public interface EmbeddingProvider {

    /** Embed a single text. Prefer {@link #embedAll} for batches to reduce API calls. */
    float[] embed(String text);

    /**
     * Embed a batch of texts in a single API call.
     * Returned list is parallel to the input list.
     */
    List<float[]> embedAll(List<String> texts);

    /** Vector dimensionality produced by this model (e.g. 1536 for text-embedding-3-small). */
    int dimension();

    /** Model identifier written into embedding_model column (e.g. "text-embedding-3-small"). */
    String modelId();
}
