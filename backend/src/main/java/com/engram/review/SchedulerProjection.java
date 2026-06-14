package com.engram.review;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maintains concept_scheduler_state — the mutable projection derived from the append-only log.
 *
 * Both the incremental updater and the replay rebuild call the same pure apply() function,
 * guaranteeing they produce identical domain state given the same event sequence.
 *
 * Replay ordering: occurred_at ASC, seq ASC (seq is the stable tiebreaker for same-timestamp events).
 * Out-of-order event arrival (event inserted with an older occurred_at after a later one was applied)
 * is out of scope for ENG-2 — guard this at ENG-5 or flag as a known limitation.
 *
 * FSRS math is ENG-5. ENG-2 only carries through the scheduler snapshot already on the event.
 * Raw outcome (isCorrect, score) is the source of truth; fsrsRating is never read on replay.
 */
public class SchedulerProjection {

    private final JdbcTemplate jdbc;

    public SchedulerProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Applies one new event to concept_scheduler_state (incremental update path).
     * Upserts the projection row.
     */
    public void applyEvent(ReviewEvent event) {
        ConceptSchedulerState current = findByConceptAndUser(event.conceptId(), event.userId());
        ConceptSchedulerState next = apply(event, current);
        upsert(next);
    }

    /**
     * Rebuilds concept_scheduler_state for a concept+user by replaying all their events from scratch.
     * Raw outcome is the source of truth; the stored fsrs_rating is never read here.
     */
    public ConceptSchedulerState rebuild(UUID conceptId, UUID userId) {
        List<ReviewEvent> events = jdbc.query(
                "SELECT * FROM review_event WHERE concept_id = ? AND user_id = ? ORDER BY occurred_at, seq",
                ReviewEventRepository.rowMapper(),
                conceptId, userId
        );
        ConceptSchedulerState state = null;
        for (ReviewEvent e : events) {
            state = apply(e, state);
        }
        return state;
    }

    /**
     * Pure function: given one event and the current state (null = first event), returns the next state.
     * Both incremental and replay call this — the only place projection logic lives.
     */
    static ConceptSchedulerState apply(ReviewEvent event, ConceptSchedulerState current) {
        int reviewCount = current == null ? 1 : current.reviewCount() + 1;
        return new ConceptSchedulerState(
                event.conceptId(),
                event.userId(),
                event.stabilityAfter(),
                event.difficultyAfter(),
                event.dueAt(),
                event.retrievabilityAtReview(),
                event.schedulerVersion(),
                event.eventId(),
                event.occurredAt(),
                reviewCount,
                Instant.now()
        );
    }

    /** Reads the current projection row from the DB. Null if no review has been recorded yet. */
    public ConceptSchedulerState read(UUID conceptId, UUID userId) {
        return findByConceptAndUser(conceptId, userId);
    }

    private ConceptSchedulerState findByConceptAndUser(UUID conceptId, UUID userId) {
        List<ConceptSchedulerState> rows = jdbc.query(
                "SELECT * FROM concept_scheduler_state WHERE concept_id = ? AND user_id = ?",
                (rs, __) -> new ConceptSchedulerState(
                        UUID.fromString(rs.getString("concept_id")),
                        UUID.fromString(rs.getString("user_id")),
                        nullableDouble(rs.getObject("stability")),
                        nullableDouble(rs.getObject("difficulty")),
                        rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant(),
                        nullableDouble(rs.getObject("retrievability_last")),
                        rs.getString("scheduler_version"),
                        rs.getString("last_event_id") == null ? null : UUID.fromString(rs.getString("last_event_id")),
                        rs.getTimestamp("last_reviewed_at") == null ? null : rs.getTimestamp("last_reviewed_at").toInstant(),
                        rs.getInt("review_count"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                conceptId, userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void upsert(ConceptSchedulerState s) {
        jdbc.update("""
                INSERT INTO concept_scheduler_state (
                    concept_id, user_id, stability, difficulty, due_at,
                    retrievability_last, scheduler_version,
                    last_event_id, last_reviewed_at, review_count, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (concept_id) DO UPDATE SET
                    user_id             = EXCLUDED.user_id,
                    stability           = EXCLUDED.stability,
                    difficulty          = EXCLUDED.difficulty,
                    due_at              = EXCLUDED.due_at,
                    retrievability_last = EXCLUDED.retrievability_last,
                    scheduler_version   = EXCLUDED.scheduler_version,
                    last_event_id       = EXCLUDED.last_event_id,
                    last_reviewed_at    = EXCLUDED.last_reviewed_at,
                    review_count        = EXCLUDED.review_count,
                    updated_at          = EXCLUDED.updated_at
                """,
                s.conceptId(), s.userId(), s.stability(), s.difficulty(), ts(s.dueAt()),
                s.retrievabilityLast(), s.schedulerVersion(),
                s.lastEventId(), ts(s.lastReviewedAt()), s.reviewCount(), ts(s.updatedAt())
        );
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Double nullableDouble(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }
}