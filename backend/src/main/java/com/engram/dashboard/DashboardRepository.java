package com.engram.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fetches all concepts for a user joined with their current scheduler state.
 * Single query — no N+1.
 */
public class DashboardRepository {

    /**
     * One row per concept. scheduler fields are null when no review has been recorded
     * (the concept is unseeded and has no concept_scheduler_state row).
     */
    record Row(
            UUID    conceptId,
            String  title,
            String  topicTag,
            String  lifecycleState,
            Double  stability,        // null if unseeded
            Double  difficulty,       // null if unseeded
            Instant lastReviewedAt    // null if unseeded
    ) {}

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> findAllWithState(UUID userId) {
        return jdbc.query("""
                SELECT
                    cc.concept_id,
                    cc.title,
                    cc.topic_tag,
                    cc.lifecycle_state,
                    css.stability          AS css_stability,
                    css.difficulty         AS css_difficulty,
                    css.last_reviewed_at   AS css_last_reviewed_at
                FROM concept_candidate cc
                LEFT JOIN concept_scheduler_state css
                    ON cc.concept_id = css.concept_id
                WHERE cc.user_id = ?
                ORDER BY cc.topic_tag, cc.title
                """,
                (rs, __) -> new Row(
                        UUID.fromString(rs.getString("concept_id")),
                        rs.getString("title"),
                        rs.getString("topic_tag"),
                        rs.getString("lifecycle_state"),
                        nullableDouble(rs.getObject("css_stability")),
                        nullableDouble(rs.getObject("css_difficulty")),
                        toInstant(rs.getTimestamp("css_last_reviewed_at"))
                ),
                userId);
    }

    private static Double nullableDouble(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
