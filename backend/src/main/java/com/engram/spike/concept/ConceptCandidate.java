package com.engram.spike.concept;

/**
 * A candidate concept — the output of the cheap ingest pass. One atomic,
 * recallable idea, with a pointer back to the source span it came from.
 * In production (ENG-4) it also carries an embedding and renders as an
 * "unseeded seed" on the dashboard. The spike keeps only what it needs.
 */
public record ConceptCandidate(
        String title,
        String topicTag,
        String sourceSpan
) {}
