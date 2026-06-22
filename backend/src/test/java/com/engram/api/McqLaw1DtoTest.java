package com.engram.api;

import com.engram.activation.ActivatedCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Law 1 (carried forward from ENG-8b, reaffirmed by ENG-9): the correct answer must NOT ship to the
 * client in the pre-commit card payload. Grading is server-side; the client only learns the answer
 * AFTER it commits a pick (via the /submit-mcq response) or after /reveal.
 *
 * Pure serialization test — no DB, no LLM. Asserts the wire shape of the two pre-commit MCQ payloads
 * (POST /api/activate and GET /api/review/next) carries options but no correctness signal.
 */
class McqLaw1DtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CORRECT = "Long-term memory retention";
    private static final List<String> DISTRACTORS =
            List.of("Short-term memorization", "Random guessing", "Passive re-reading");

    private static ActivatedCard card() {
        return new ActivatedCard(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "What does spaced repetition optimize?", CORRECT, DISTRACTORS,
                "fake-model", "professor-v1/distractor-v1",
                0, 0, 0L, "idem-1", Instant.now());
    }

    @Test
    void activatePayload_carriesNoCorrectnessSignal() throws Exception {
        String json = MAPPER.writeValueAsString(ActivationController.ActivateResponse.from(card()));
        assertNoCorrectnessSignal(json);
    }

    @Test
    void reviewNextMcqPayload_carriesNoCorrectnessSignal() throws Exception {
        String json = MAPPER.writeValueAsString(ReviewController.NextCardResponse.fromMcq(card()));
        assertNoCorrectnessSignal(json);
    }

    private static void assertNoCorrectnessSignal(String json) throws Exception {
        // No field that would flag or hand over the answer. (A null "answer":null key on the cloze
        // shape is fine — it carries no value; what matters is the answer VALUE is never shipped.)
        assertFalse(json.contains("correctAnswer"),
                "pre-commit payload must not contain a 'correctAnswer' field: " + json);

        // The correct answer string appears EXACTLY ONCE — inside options, unlabeled. If it were
        // duplicated into a dedicated field, this count would be 2+.
        assertEquals(1, countOccurrences(json, CORRECT),
                "the correct answer must appear exactly once (inside options, unlabeled): " + json);

        // Options are bare strings (no per-option correctness marker), exactly 4, and include the
        // correct answer somewhere among them.
        var node = MAPPER.readTree(json);
        var options = node.get("options");
        assertNotNull(options, "payload must carry options");
        assertEquals(4, options.size(), "MCQ payload must carry exactly 4 options");
        boolean correctIsPresent = false;
        for (var opt : options) {
            assertTrue(opt.isTextual(), "each option must be a bare string, not an object with a flag");
            if (opt.asText().equals(CORRECT)) correctIsPresent = true;
        }
        assertTrue(correctIsPresent, "the correct answer is among the options (unlabeled)");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
