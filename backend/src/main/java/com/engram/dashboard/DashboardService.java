package com.engram.dashboard;

import com.engram.scheduler.Fsrs;
import com.engram.scheduler.FsrsState;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardService {

    private final DashboardRepository repo;
    private final Fsrs fsrs;

    public DashboardService(DashboardRepository repo, Fsrs fsrs) {
        this.repo = repo;
        this.fsrs = fsrs;
    }

    public DashboardView getDashboard(UUID userId) {
        List<DashboardRepository.Row> rows = repo.findAllWithState(userId);
        Instant now = Instant.now();

        // ── per-concept retrievability ────────────────────────────────────────
        record Enriched(String title, String topicTag, String lifecycleState,
                        Double retrievability, MoodTier tier) {}

        List<Enriched> enriched = rows.stream().map(row -> {
            boolean isSeeded = "SEEDED".equals(row.lifecycleState());
            Double r = null;
            MoodTier tier = null;
            if (isSeeded && row.stability() != null && row.lastReviewedAt() != null) {
                FsrsState state = new FsrsState(row.stability(), row.difficulty(), row.lastReviewedAt());
                r = fsrs.retrievability(state, now);
                tier = TierThresholds.classify(r);
            }
            return new Enriched(row.title(), row.topicTag(), row.lifecycleState(), r, tier);
        }).toList();

        // ── global stats ──────────────────────────────────────────────────────
        long seededCount = enriched.stream()
                .filter(e -> "SEEDED".equals(e.lifecycleState())).count();
        long retrievableCount = enriched.stream()
                .filter(e -> e.retrievability() != null
                        && e.retrievability() >= TierThresholds.RETRIEVABLE_THRESHOLD)
                .count();
        long frontierCount = enriched.stream()
                .filter(e -> !"SEEDED".equals(e.lifecycleState())).count();

        DashboardMode mode = seededCount >= 1
                ? DashboardMode.STEADY_STATE : DashboardMode.COLD_START;
        Double livingKnowledgePct = (mode == DashboardMode.STEADY_STATE && seededCount > 0)
                ? (double) retrievableCount / (double) seededCount * 100.0
                : null;

        // ── gardens grouped by topic_tag ──────────────────────────────────────
        Map<String, List<Enriched>> byTag = enriched.stream().collect(
                Collectors.groupingBy(
                        e -> (e.topicTag() != null && !e.topicTag().isBlank())
                                ? e.topicTag() : "Uncategorized",
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<GardenView> gardens = byTag.entrySet().stream().map(entry -> {
            String tag = entry.getKey();
            List<Enriched> tagConcepts = entry.getValue();

            int tagSeeded   = (int) tagConcepts.stream().filter(e -> "SEEDED".equals(e.lifecycleState())).count();
            int tagFrontier = tagConcepts.size() - tagSeeded;

            // avg retrievability of seeded concepts only; null for all-unseeded gardens
            OptionalDouble avg = tagConcepts.stream()
                    .filter(e -> e.retrievability() != null)
                    .mapToDouble(Enriched::retrievability)
                    .average();
            Double avgR    = avg.isPresent() ? avg.getAsDouble() : null;
            MoodTier rolledUp = avgR != null ? TierThresholds.classify(avgR) : null;

            List<ConceptView> conceptViews = tagConcepts.stream()
                    .map(e -> new ConceptView(e.title(), e.lifecycleState(), e.retrievability(), e.tier()))
                    .toList();

            return new GardenView(tag, tagConcepts.size(), tagSeeded, tagFrontier,
                    avgR, rolledUp, conceptViews);
        }).toList();

        return new DashboardView(mode, livingKnowledgePct,
                (int) seededCount, (int) retrievableCount, (int) frontierCount, gardens);
    }
}
