package com.engram.concept;

import com.engram.ingest.IngestedDocument;
import com.engram.ingest.SourceAdapter;
import com.engram.ingest.SyncDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CandidateIngestionService {

    private final Extractor extractor;
    private final ConceptCandidateRepository repo;

    public CandidateIngestionService(Extractor extractor, ConceptCandidateRepository repo) {
        this.extractor = extractor;
        this.repo = repo;
    }

    public IngestionSummary ingest(SourceAdapter adapter, UUID userId) {
        List<IngestedDocument> current = adapter.scan();
        Map<String, String> priorHashes = repo.loadPriorHashes(userId, adapter.type());
        SyncDiff.SyncResult diff = SyncDiff.diff(current, priorHashes);

        int candidatesCreated = 0;
        int llmCallsMade = 0;

        for (IngestedDocument doc : diff.added()) {
            List<ExtractedConcept> concepts = dedupeByTitle(extractor.extract(doc));
            llmCallsMade++;
            repo.upsertAll(userId, doc.sourceType(), doc.sourceRef(), doc.contentHash(), concepts);
            candidatesCreated += concepts.size();
        }

        for (IngestedDocument doc : diff.changed()) {
            repo.deleteByDoc(userId, doc.sourceType(), doc.sourceRef());
            List<ExtractedConcept> concepts = dedupeByTitle(extractor.extract(doc));
            llmCallsMade++;
            repo.upsertAll(userId, doc.sourceType(), doc.sourceRef(), doc.contentHash(), concepts);
            candidatesCreated += concepts.size();
        }

        for (String ref : diff.removed()) {
            repo.deleteByDoc(userId, adapter.type(), ref);
        }

        return new IngestionSummary(
                diff.added().size(),
                diff.changed().size(),
                diff.unchanged().size(),
                diff.removed().size(),
                candidatesCreated,
                llmCallsMade);
    }

    // Last-wins on duplicate title — matches DB ON CONFLICT DO UPDATE behavior.
    private static List<ExtractedConcept> dedupeByTitle(List<ExtractedConcept> concepts) {
        LinkedHashMap<String, ExtractedConcept> map = new LinkedHashMap<>();
        for (ExtractedConcept c : concepts) {
            map.put(c.title(), c);
        }
        return new ArrayList<>(map.values());
    }
}