package com.engram.review;

import com.engram.TestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewEventRepositoryTest {

    private static final DataSource ds = TestDatabase.dataSource();

    private JdbcTemplate jdbc;
    private ReviewEventRepository repo;
    private SchedulerProjection projection;

    @BeforeEach
    void setUp() {
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        repo = new ReviewEventRepository(jdbc);
        projection = new SchedulerProjection(jdbc);
    }

    // ── Test 1: append-only trigger rejects UPDATE and DELETE ────────────────

    @Test
    void appendOnlyTrigger_rejectsUpdate() {
        ReviewEvent e = minimalEvent(UUID.randomUUID(), UUID.randomUUID());
        repo.append(e);

        var ex = assertThrows(Exception.class, () ->
                jdbc.update("UPDATE review_event SET format = 'cloze' WHERE event_id = ?::uuid", e.eventId().toString())
        );
        assertTrue(ex.getMessage().contains("append-only"),
                "trigger message should mention append-only; got: " + ex.getMessage());
    }

    @Test
    void appendOnlyTrigger_rejectsDelete() {
        ReviewEvent e = minimalEvent(UUID.randomUUID(), UUID.randomUUID());
        repo.append(e);

        var ex = assertThrows(Exception.class, () ->
                jdbc.update("DELETE FROM review_event WHERE event_id = ?::uuid", e.eventId().toString())
        );
        assertTrue(ex.getMessage().contains("append-only"),
                "trigger message should mention append-only; got: " + ex.getMessage());
    }

    // ── Test 2: idempotent insert (same client_event_id) ─────────────────────

    @Test
    void idempotentInsert_duplicateClientEventId_noSecondRow() {
        UUID userId    = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        String clientId = UUID.randomUUID().toString();

        ReviewEvent e = event(UUID.randomUUID(), clientId, userId, conceptId, Instant.now());
        boolean first  = repo.append(e);
        boolean second = repo.append(e);

        assertTrue(first,   "first insert should return true");
        assertFalse(second, "duplicate insert should return false");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_event WHERE user_id = ?::uuid AND client_event_id = ?",
                Integer.class, userId.toString(), clientId);
        assertEquals(1, count, "exactly one row after idempotent replay");
    }

    // ── Test 3: replay reconstructs concept_scheduler_state identically ──────

    @Test
    void replay_matchesIncremental_forThreeEvents() {
        UUID userId    = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");

        ReviewEvent e1 = eventWithScheduler(UUID.randomUUID(), userId, conceptId, t0,              3.0, 0.5);
        ReviewEvent e2 = eventWithScheduler(UUID.randomUUID(), userId, conceptId, t0.plusSeconds(1), 4.5, 0.7);
        ReviewEvent e3 = eventWithScheduler(UUID.randomUUID(), userId, conceptId, t0.plusSeconds(2), 6.0, 0.8);

        // Incremental: insert each event and apply to projection.
        repo.append(e1); projection.applyEvent(e1);
        repo.append(e2); projection.applyEvent(e2);
        repo.append(e3); projection.applyEvent(e3);

        // Read the ACTUAL row that applyEvent() wrote to concept_scheduler_state.
        ConceptSchedulerState afterIncremental = projection.read(conceptId, userId);

        // Wipe the projection and rebuild purely from the event log.
        jdbc.update("DELETE FROM concept_scheduler_state WHERE concept_id = ?::uuid", conceptId.toString());
        ConceptSchedulerState afterReplay = projection.rebuild(conceptId, userId);

        // Domain fields must match. updatedAt is metadata — intentionally excluded.
        assertSchedulerFieldsEqual(afterIncremental, afterReplay);
    }

    // ── Test 4: tombstone nulls ciphertext, leaves review_event intact ────────

    @Test
    void tombstone_nullsCiphertext_butLeavesEventQueryable() {
        UUID userId    = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        ReviewEvent e  = minimalEvent(conceptId, userId);
        repo.append(e);

        // Insert a payload (stub plaintext bytes; real impl would encrypt).
        byte[] ciphertext = "stub-plaintext".getBytes();
        jdbc.update("""
                INSERT INTO review_event_answer_payload
                    (event_id, user_id, ciphertext, encryption_key_id, retain_until)
                VALUES (?::uuid, ?::uuid, ?, 'key-v1', ?)
                """,
                e.eventId().toString(), userId.toString(), ciphertext,
                java.sql.Timestamp.from(Instant.now().plusSeconds(86400)));

        // Tombstone: null out the ciphertext.
        jdbc.update("""
                UPDATE review_event_answer_payload
                SET ciphertext = NULL, tombstoned_at = now()
                WHERE event_id = ?::uuid
                """, e.eventId().toString());

        // Payload: ciphertext must be null, tombstoned_at must be set.
        jdbc.query(
                "SELECT ciphertext, tombstoned_at FROM review_event_answer_payload WHERE event_id = ?::uuid",
                rs -> {
                    rs.getBytes("ciphertext");
                    assertTrue(rs.wasNull(), "ciphertext must be null after tombstone");
                    assertNotNull(rs.getTimestamp("tombstoned_at"), "tombstoned_at must be set");
                },
                e.eventId().toString()
        );

        // review_event must still exist and be fully queryable.
        List<ReviewEvent> events = repo.findByConcept(conceptId);
        assertEquals(1, events.size(), "review_event row must survive tombstone");
        assertEquals(e.eventId(), events.get(0).eventId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ReviewEvent minimalEvent(UUID conceptId, UUID userId) {
        return event(UUID.randomUUID(), UUID.randomUUID().toString(), userId, conceptId, Instant.now());
    }

    private static ReviewEvent event(UUID eventId, String clientEventId,
                                     UUID userId, UUID conceptId, Instant occurredAt) {
        return new ReviewEvent(
                eventId, 0L, clientEventId, userId, conceptId, occurredAt,
                null, null, "mcq", null,
                true, null, false,
                3, null, null, null, null,
                null, null, null, null, null
        );
    }

    private static ReviewEvent eventWithScheduler(UUID eventId, UUID userId, UUID conceptId,
                                                   Instant occurredAt, double stability, double retrievability) {
        return new ReviewEvent(
                eventId, 0L, UUID.randomUUID().toString(), userId, conceptId, occurredAt,
                null, null, "cloze", null,
                true, null, false,
                3, null, null, null, null,
                stability, 5.0, occurredAt.plusSeconds(86400L * (long) stability),
                retrievability, "spike-v0"
        );
    }

    private static void assertSchedulerFieldsEqual(ConceptSchedulerState a, ConceptSchedulerState b) {
        assertEquals(a.conceptId(),          b.conceptId(),          "conceptId");
        assertEquals(a.userId(),             b.userId(),             "userId");
        assertEquals(a.stability(),          b.stability(),          "stability");
        assertEquals(a.difficulty(),         b.difficulty(),         "difficulty");
        assertEquals(a.dueAt(),              b.dueAt(),              "dueAt");
        assertEquals(a.retrievabilityLast(), b.retrievabilityLast(), "retrievabilityLast");
        assertEquals(a.schedulerVersion(),   b.schedulerVersion(),   "schedulerVersion");
        assertEquals(a.lastEventId(),        b.lastEventId(),        "lastEventId");
        assertEquals(a.lastReviewedAt(),     b.lastReviewedAt(),     "lastReviewedAt");
        assertEquals(a.reviewCount(),        b.reviewCount(),        "reviewCount");
    }
}
