package com.engram.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic test double. Never hits the network.
 * Hashes the input text to a stable pseudo-vector of the right dimension, then L2-normalises it
 * so cosine distance behaves correctly in kNN tests (unit vectors → cosine == dot product).
 *
 * Call count is exposed via {@link #callCount()} so tests can assert zero-embed discipline.
 */
public class FakeEmbeddingProvider implements EmbeddingProvider {

    private static final int DIM = 1536;
    private static final String MODEL = "fake-embedding-test";

    private int callCount = 0;

    @Override
    public float[] embed(String text) {
        callCount++;
        return pseudoVector(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        callCount += texts.size();
        List<float[]> result = new ArrayList<>(texts.size());
        for (String t : texts) {
            result.add(pseudoVector(t));
        }
        return result;
    }

    @Override
    public int dimension() { return DIM; }

    @Override
    public String modelId() { return MODEL; }

    /** Total individual texts embedded since construction. */
    public int callCount() { return callCount; }

    public void resetCallCount() { callCount = 0; }

    // ── internals ────────────────────────────────────────────────────────────

    private static float[] pseudoVector(String text) {
        float[] v = new float[DIM];
        // Spread the hash across dimensions deterministically.
        int h = text.hashCode();
        for (int i = 0; i < DIM; i++) {
            // Mix hash with dimension index for spread; cast to float in (-1,1).
            int mixed = h ^ (i * 0x9e3779b9);
            v[i] = (mixed & 0xffff) / 32768.0f - 1.0f;
        }
        return l2normalize(v);
    }

    private static float[] l2normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        float norm = (float) Math.sqrt(sum);
        if (norm < 1e-10f) return v;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }
}
