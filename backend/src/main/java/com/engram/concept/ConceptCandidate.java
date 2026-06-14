package com.engram.concept;

import com.engram.ingest.SourceType;

import java.time.Instant;
import java.util.UUID;

public record ConceptCandidate(
        UUID           conceptId,
        UUID           userId,
        SourceType     sourceType,
        String         sourceRef,
        String         sourceContentHash,
        String         title,
        String         topicTag,
        String         sourceSpan,
        LifecycleState lifecycleState,
        Instant        createdAt,
        Instant        updatedAt
) {}