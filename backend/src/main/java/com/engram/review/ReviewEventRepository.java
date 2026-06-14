package com.engram.review;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for review_event.
 * Uses JdbcTemplate (explicit SQL) — no ORM. Append-only log fights mutable-entity model.
 * Insert is idempotent on (user_id, client_event_id): duplicate replay is safe.
 */
public class ReviewEventRepository {

    private final JdbcTemplate jdbc;

    public ReviewEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends an event. Idempotent: a duplicate (user_id, client_event_id) is silently ignored.
     * Returns true if the row was inserted, false if it already existed.
     */
    public boolean append(ReviewEvent e) {
        int rows = jdbc.update("""
                INSERT INTO review_event (
                    event_id, client_event_id, user_id, concept_id, occurred_at,
                    session_id, session_type, format, response_latency_ms,
                    is_correct, score, hint_used,
                    fsrs_rating, grading_scheme_version, expected_answer_ref,
                    grader_prompt_version, model_id,
                    stability_after, difficulty_after, due_at,
                    retrievability_at_review, scheduler_version
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    ?, ?, ?,
                    ?, ?
                )
                ON CONFLICT (user_id, client_event_id) DO NOTHING
                """,
                e.eventId(), e.clientEventId(), e.userId(), e.conceptId(), ts(e.occurredAt()),
                e.sessionId(), e.sessionType(), e.format(), e.responseLatencyMs(),
                e.isCorrect(), e.score(), e.hintUsed(),
                e.fsrsRating(), e.gradingSchemeVersion(), e.expectedAnswerRef(),
                e.graderPromptVersion(), e.modelId(),
                e.stabilityAfter(), e.difficultyAfter(), ts(e.dueAt()),
                e.retrievabilityAtReview(), e.schedulerVersion()
        );
        return rows == 1;
    }

    /** All events for a concept, in replay order (occurred_at ASC, seq ASC). */
    public List<ReviewEvent> findByConcept(UUID conceptId) {
        return jdbc.query(
                "SELECT * FROM review_event WHERE concept_id = ? ORDER BY occurred_at, seq",
                ROW_MAPPER,
                conceptId
        );
    }

    /** Events for a user within a time window, most-recent first. */
    public List<ReviewEvent> findByUserAndTimeRange(UUID userId, Instant from, Instant to) {
        return jdbc.query(
                "SELECT * FROM review_event WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, seq DESC",
                ROW_MAPPER,
                userId, ts(from), ts(to)
        );
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static final RowMapper<ReviewEvent> ROW_MAPPER = (rs, __) -> map(rs);

    /** Package-visible for SchedulerProjection replay queries. */
    static RowMapper<ReviewEvent> rowMapper() { return ROW_MAPPER; }

    private static ReviewEvent map(ResultSet rs) throws SQLException {
        return new ReviewEvent(
                uuid(rs, "event_id"),
                rs.getLong("seq"),
                rs.getString("client_event_id"),
                uuid(rs, "user_id"),
                uuid(rs, "concept_id"),
                instant(rs, "occurred_at"),
                uuid(rs, "session_id"),
                rs.getString("session_type"),
                rs.getString("format"),
                nullable(rs, "response_latency_ms", Integer.class),
                rs.getBoolean("is_correct"),
                nullable(rs, "score", Double.class),
                rs.getBoolean("hint_used"),
                nullable(rs, "fsrs_rating", Integer.class),
                rs.getString("grading_scheme_version"),
                rs.getString("expected_answer_ref"),
                rs.getString("grader_prompt_version"),
                rs.getString("model_id"),
                nullable(rs, "stability_after", Double.class),
                nullable(rs, "difficulty_after", Double.class),
                instant(rs, "due_at"),
                nullable(rs, "retrievability_at_review", Double.class),
                rs.getString("scheduler_version")
        );
    }

    private static UUID uuid(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v == null ? null : UUID.fromString(v);
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullable(ResultSet rs, String col, Class<T> type) throws SQLException {
        Object v = rs.getObject(col);
        if (rs.wasNull()) return null;
        if (type == Double.class && v instanceof Number n) return (T) Double.valueOf(n.doubleValue());
        if (type == Integer.class && v instanceof Number n) return (T) Integer.valueOf(n.intValue());
        return type.cast(v);
    }
}