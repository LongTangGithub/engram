package com.engram.concept;

public record IngestionSummary(
        int docsAdded,
        int docsChanged,
        int docsUnchanged,
        int docsRemoved,
        int candidatesCreated,
        int llmCallsMade
) {}