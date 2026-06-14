package com.engram.concept;

import com.engram.ingest.SourceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConceptCandidateRepository {

    private final JdbcTemplate jdbc;

    public ConceptCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a batch of candidates for a doc. ON CONFLICT updates metadata
     * (hash, topicTag, sourceSpan) in place. Used for ADDED docs.
     */
    public void upsertAll(UUID userId, SourceType sourceType, String sourceRef,
                          String sourceContentHash, List<ExtractedConcept> concepts) {
        for (ExtractedConcept c : concepts) {
            jdbc.update("""
                    INSERT INTO concept_candidate
                        (user_id, source_type, source_ref, source_content_hash,
                         title, topic_tag, source_span, lifecycle_state)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'CANDIDATE')
                    ON CONFLICT (user_id, source_type, source_ref, title) DO UPDATE SET
                        source_content_hash = EXCLUDED.source_content_hash,
                        topic_tag           = EXCLUDED.topic_tag,
                        source_span         = EXCLUDED.source_span,
                        updated_at          = now()
                    """,
                    userId, sourceType.name(), sourceRef, sourceContentHash,
                    c.title(), c.topicTag(), c.sourceSpan());
        }
    }

    /** Delete all candidates belonging to a specific doc. Used for CHANGED and REMOVED docs. */
    public int deleteByDoc(UUID userId, SourceType sourceType, String sourceRef) {
        return jdbc.update(
                "DELETE FROM concept_candidate WHERE user_id = ? AND source_type = ? AND source_ref = ?",
                userId, sourceType.name(), sourceRef);
    }

    /**
     * Returns a map of sourceRef → sourceContentHash for a user+source.
     * Used by CandidateIngestionService to build the prior-hashes map for SyncDiff.
     */
    public Map<String, String> loadPriorHashes(UUID userId, SourceType sourceType) {
        return jdbc.query(
                "SELECT source_ref, MAX(source_content_hash) AS source_content_hash FROM concept_candidate WHERE user_id = ? AND source_type = ? GROUP BY source_ref",
                (rs, n) -> Map.entry(rs.getString("source_ref"), rs.getString("source_content_hash")),
                userId, sourceType.name()
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    /**
     * Picks the next concept to review for a user.
     * Prefers never-reviewed (no scheduler state row) via NULLS FIRST, then most overdue.
     * ENG-14 adds near-cliff smart selection; this is intentionally minimal.
     */
    public Optional<ConceptCandidate> findNextDue(UUID userId) {
        List<ConceptCandidate> rows = jdbc.query("""
                SELECT cc.* FROM concept_candidate cc
                LEFT JOIN concept_scheduler_state css ON cc.concept_id = css.concept_id
                WHERE cc.user_id = ?
                ORDER BY css.due_at ASC NULLS FIRST
                LIMIT 1
                """, rowMapper(), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Flips lifecycle from CANDIDATE → SEEDED after the first review.
     *
     * NOTE (ENG-8 decision): ACTIVATED (AI card generated) and SEEDED (first reviewed) may be
     * orthogonal states, not a linear sequence. Do NOT assume CANDIDATE→ACTIVATED→SEEDED ordering
     * until ENG-8 defines the relationship. This method only handles the SEEDED transition.
     */
    public void flipToSeeded(UUID conceptId) {
        jdbc.update("""
                UPDATE concept_candidate SET lifecycle_state = 'SEEDED', updated_at = now()
                WHERE concept_id = ?
                """, conceptId);
    }

    public List<ConceptCandidate> findByDoc(UUID userId, SourceType sourceType, String sourceRef) {
        return jdbc.query(
                "SELECT * FROM concept_candidate WHERE user_id = ? AND source_type = ? AND source_ref = ? ORDER BY created_at",
                rowMapper(),
                userId, sourceType.name(), sourceRef);
    }

    static RowMapper<ConceptCandidate> rowMapper() {
        return (rs, n) -> new ConceptCandidate(
                uuid(rs, "concept_id"),
                uuid(rs, "user_id"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_ref"),
                rs.getString("source_content_hash"),
                rs.getString("title"),
                rs.getString("topic_tag"),
                rs.getString("source_span"),
                LifecycleState.valueOf(rs.getString("lifecycle_state")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static UUID uuid(ResultSet rs, String col) throws SQLException {
        return UUID.fromString(rs.getString(col));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }
}