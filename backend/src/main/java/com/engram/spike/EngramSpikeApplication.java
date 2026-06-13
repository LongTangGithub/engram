package com.engram.spike;

import com.engram.spike.concept.ActivatedCard;
import com.engram.spike.concept.ConceptCandidate;
import com.engram.spike.generation.GenerationOrchestrator;
import com.engram.spike.ingest.CandidateExtractor;
import com.engram.spike.llm.CostLog;
import com.engram.spike.review.ReviewEvent;
import com.engram.spike.scheduler.FsrsReadback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ENG-1 — the de-risking spike.
 *
 * Runs the full core loop on ONE real note:
 *   ingest -> candidate extraction -> deep activation (Professor + Distractor)
 *   -> write a review event -> read it back through FSRS.
 *
 * Output: (1) the real $/activation cost report, and (2) the generated card
 * (question, answer, distractors) to eyeball for quality.
 *
 * Usage:
 *   ANTHROPIC_API_KEY=sk-... ./gradlew bootRun --args="sample-note.md"
 * If no path is given, defaults to sample-note.md in the working directory.
 */
@SpringBootApplication
public class EngramSpikeApplication implements CommandLineRunner {

    private final CandidateExtractor extractor;
    private final GenerationOrchestrator orchestrator;
    private final FsrsReadback fsrs;
    private final CostLog costLog;

    public EngramSpikeApplication(CandidateExtractor extractor,
                                  GenerationOrchestrator orchestrator,
                                  FsrsReadback fsrs,
                                  CostLog costLog) {
        this.extractor = extractor;
        this.orchestrator = orchestrator;
        this.fsrs = fsrs;
        this.costLog = costLog;
    }

    public static void main(String[] args) {
        SpringApplication.run(EngramSpikeApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String notePath = args.length > 0 ? args[0] : "sample-note.md";
        String note = Files.readString(Path.of(notePath));

        line();
        System.out.println("ENG-1 spike — note: " + notePath + " (" + note.length() + " chars)");
        line();

        // 1) cheap candidate extraction
        List<ConceptCandidate> candidates = extractor.extract(note);
        System.out.println("Extracted " + candidates.size() + " candidate concept(s):");
        candidates.forEach(c -> System.out.println("  - " + c.title() + "  [" + c.topicTag() + "]"));

        if (candidates.isEmpty()) {
            System.out.println("No candidates — nothing to activate. Check the note or the Extractor prompt.");
            return;
        }

        // 2) deep activation of ONE candidate (one activation = the metered unit)
        ConceptCandidate target = candidates.get(0);
        System.out.println("\nActivating: " + target.title());
        ActivatedCard card = orchestrator.activate(target, note);

        // ---- QUALITY ARTIFACT (eyeball this) ----
        line();
        System.out.println("GENERATED CARD (judge the quality):");
        System.out.println("  Q: " + card.question());
        System.out.println("  A: " + card.answer());
        System.out.println("  Distractors:");
        card.distractors().forEach(d -> System.out.println("    x " + d));
        line();

        // 3) write a review event (simulate a 'Good' self-grade) — the immutable fact
        ReviewEvent event = new ReviewEvent(
                UUID.randomUUID().toString(),
                card.conceptTitle(),
                Instant.now(),
                "mcq",
                true,   // raw outcome
                3       // derived rating: Good
        );
        System.out.println("Wrote review event: " + event.eventId() + "  (rating=" + event.fsrsRating() + ")");

        // 4) read it back through FSRS
        FsrsReadback.SchedulerState state = fsrs.readBack(event);
        System.out.printf("FSRS read-back: stability=%.1fd  retrievability=%.2f  due in ~%.1fd%n",
                state.stabilityDays(), state.retrievabilityNow(), state.dueInDays());

        // ---- COST ARTIFACT (the headline number) ----
        System.out.println(costLog.report());

        System.out.println("""
                Spike verdict checklist:
                  [ ] Is the cost/activation acceptable vs a $9/mo sub at ~500 activations fair-use?
                  [ ] Are the distractors plausible-but-wrong, or obviously off?
                  [ ] Is the question genuine recall (not trivia), and the answer faithful to the note?
                If cost or quality fails, fix the assumption BEFORE building Phase 1.
                """);
    }

    private static void line() {
        System.out.println("---------------------------------------------------------------");
    }
}
