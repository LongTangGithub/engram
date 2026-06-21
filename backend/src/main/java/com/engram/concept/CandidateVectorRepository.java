package com.engram.concept;

import com.engram.embedding.EmbeddingProvider;
import com.engram.ingest.SourceType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * pgvector kNN queries and backfill for concept_candidate embeddings.
 *
 * <p>Cosine distance via the {@code <=>} operator (vector_cosine_ops HNSW index).
 * Tune recall vs speed at query time: {@code SET hnsw.ef_search = 100} before the query.
 * The default ef_search (40) is sufficient at this scale.
 */
public class CandidateVectorRepository {

    private final JdbcTemplate jdbc;

    public CandidateVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the k nearest neighbors of {@code conceptId} for {@code userId},
     * ordered by ascending cosine distance (most similar first).
     *
     * <p>Invariants:
     * <ul>
     *   <li>Scoped to {@code userId} — never returns another user's concepts.</li>
     *   <li>Excludes the query concept itself.</li>
     *   <li>Only considers rows WHERE embedding IS NOT NULL.</li>
     *   <li>Reads the stored vector of the query concept — does NOT re-embed.</li>
     * </ul>
     */
    public List<UUID> findNearestNeighbors(UUID userId, UUID conceptId, int k) {
        return jdbc.query("""
                SELECT cc.concept_id
                FROM concept_candidate cc
                WHERE cc.user_id    = ?
                  AND cc.concept_id <> ?
                  AND cc.embedding  IS NOT NULL
                ORDER BY cc.embedding <=> (
                    SELECT embedding FROM concept_candidate WHERE concept_id = ?
                )
                LIMIT ?
                """,
                (rs, n) -> UUID.fromString(rs.getString("concept_id")),
                userId, conceptId, conceptId, k);
    }

    /**
     * Backfills embeddings for all candidates (all users) where embedding IS NULL.
     * Idempotent: re-running when everything is already embedded does nothing (returns 0).
     *
     * @return number of candidates embedded
     */
    public int backfill(EmbeddingProvider provider) {
        return backfillWhere(null, provider);
    }

    /**
     * Backfills embeddings for a single user's candidates where embedding IS NULL.
     * Idempotent.
     */
    public int backfill(UUID userId, EmbeddingProvider provider) {
        return backfillWhere(userId, provider);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private record NullRow(UUID conceptId, String title, String sourceSpan) {}

    private int backfillWhere(UUID userId, EmbeddingProvider provider) {
        List<NullRow> rows;
        if (userId == null) {
            rows = jdbc.query(
                    "SELECT concept_id, title, source_span FROM concept_candidate WHERE embedding IS NULL",
                    (rs, n) -> new NullRow(
                            UUID.fromString(rs.getString("concept_id")),
                            rs.getString("title"),
                            rs.getString("source_span")));
        } else {
            rows = jdbc.query(
                    "SELECT concept_id, title, source_span FROM concept_candidate WHERE user_id = ? AND embedding IS NULL",
                    (rs, n) -> new NullRow(
                            UUID.fromString(rs.getString("concept_id")),
                            rs.getString("title"),
                            rs.getString("source_span")),
                    userId);
        }

        if (rows.isEmpty()) return 0;

        List<String> texts = rows.stream()
                .map(r -> CandidateIngestionService.toEmbedText(r.title(), r.sourceSpan()))
                .toList();
        List<float[]> vecs = provider.embedAll(texts);
        Instant now = Instant.now();

        for (int i = 0; i < rows.size(); i++) {
            jdbc.update("""
                    UPDATE concept_candidate
                    SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = ?, updated_at = now()
                    WHERE concept_id = ?
                    """,
                    ConceptCandidateRepository.formatVector(vecs.get(i)),
                    provider.modelId(),
                    Timestamp.from(now),
                    rows.get(i).conceptId());
        }
        return rows.size();
    }
}
