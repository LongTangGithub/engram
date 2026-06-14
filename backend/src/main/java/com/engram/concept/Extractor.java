package com.engram.concept;

import com.engram.ingest.IngestedDocument;

import java.util.List;

public interface Extractor {
    List<ExtractedConcept> extract(IngestedDocument doc);
}
