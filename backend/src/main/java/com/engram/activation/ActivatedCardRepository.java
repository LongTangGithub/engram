package com.engram.activation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ActivatedCardRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public ActivatedCardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ActivatedCard> findByConceptId(UUID conceptId) {
        List<ActivatedCard> rows = jdbc.query(
                "SELECT * FROM activated_card WHERE concept_id = ?",
                rowMapper(), conceptId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // Returns false if concept_id already had a row (UNIQUE conflict — concurrent race loser).
    public boolean save(ActivatedCard card) {
        int affected = jdbc.update("""
                INSERT INTO activated_card
                    (card_id, concept_id, user_id, question, correct_answer, distractors,
                     generation_model, generation_prompt_version,
                     input_tokens, output_tokens, cost_micros, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (concept_id) DO NOTHING
                """,
                card.cardId(),
                card.conceptId(),
                card.userId(),
                card.question(),
                card.correctAnswer(),
                toJson(card.distractors()),
                card.generationModel(),
                card.generationPromptVersion(),
                card.inputTokens(),
                card.outputTokens(),
                card.costMicros(),
                card.idempotencyKey(),
                Timestamp.from(card.createdAt()));
        return affected > 0;
    }

    private RowMapper<ActivatedCard> rowMapper() {
        return (rs, n) -> new ActivatedCard(
                UUID.fromString(rs.getString("card_id")),
                UUID.fromString(rs.getString("concept_id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("question"),
                rs.getString("correct_answer"),
                fromJson(rs.getString("distractors")),
                rs.getString("generation_model"),
                rs.getString("generation_prompt_version"),
                rs.getInt("input_tokens"),
                rs.getInt("output_tokens"),
                rs.getLong("cost_micros"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(List<String> list) {
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
