package com.engram.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure function: classifies a current scan against a prior-state hash map into
 * added / changed / unchanged / removed buckets.
 *
 * Persistence of prior hashes is the caller's responsibility (ENG-4).
 * ENG-3 is persistence-agnostic: no DB access here.
 *
 * Rename detection (same hash, different ref) is OUT of scope — a rename appears
 * as one removed + one added entry. Note this if rename-awareness is ever needed.
 */
public final class SyncDiff {

    private SyncDiff() {}

    /**
     * @param current        documents returned by SourceAdapter.scan()
     * @param priorHashes    map of sourceRef → contentHash from the previous scan
     * @return classified result
     */
    public static SyncResult diff(List<IngestedDocument> current, Map<String, String> priorHashes) {
        List<IngestedDocument> added     = new ArrayList<>();
        List<IngestedDocument> changed   = new ArrayList<>();
        List<String>           unchanged = new ArrayList<>();

        Set<String> currentRefs = current.stream()
                .map(IngestedDocument::sourceRef)
                .collect(Collectors.toSet());

        for (IngestedDocument doc : current) {
            String prior = priorHashes.get(doc.sourceRef());
            if (prior == null) {
                added.add(doc);
            } else if (prior.equals(doc.contentHash())) {
                unchanged.add(doc.sourceRef());
            } else {
                changed.add(doc);
            }
        }

        List<String> removed = priorHashes.keySet().stream()
                .filter(ref -> !currentRefs.contains(ref))
                .toList();

        return new SyncResult(Instant.now(), added, changed, unchanged, removed);
    }

    /**
     * Result of a single diff pass.
     *
     * @param scannedAt  when the diff was computed
     * @param added      docs with a sourceRef not present in priorHashes
     * @param changed    docs present in both, hash differs
     * @param unchanged  sourceRefs present in both, hash identical (cheap skip)
     * @param removed    sourceRefs in priorHashes but not in the current scan
     */
    public record SyncResult(
            Instant              scannedAt,
            List<IngestedDocument> added,
            List<IngestedDocument> changed,
            List<String>           unchanged,
            List<String>           removed
    ) {}
}