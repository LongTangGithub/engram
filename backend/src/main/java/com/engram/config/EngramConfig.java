package com.engram.config;

import com.engram.activation.ActivatedCardRepository;
import com.engram.activation.ActivationService;
import com.engram.activation.AnthropicClaudeClient;
import com.engram.activation.ClaudeClient;
import com.engram.activation.Distractor;
import com.engram.activation.GenerationOrchestrator;
import com.engram.activation.Professor;
import com.engram.concept.CandidateIngestionService;
import com.engram.concept.Extractor;
import com.engram.concept.CandidateVectorRepository;
import com.engram.concept.ConceptCandidateRepository;
import com.engram.concept.Extractor;
import com.engram.dashboard.DashboardRepository;
import com.engram.dashboard.DashboardService;
import com.engram.embedding.EmbeddingProvider;
import com.engram.embedding.OpenAiEmbeddingProvider;
import com.engram.quiz.ClozeGenerator;
import com.engram.quiz.ReviewService;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.Fsrs;
import com.engram.scheduler.RetrievabilityEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

@Configuration
public class EngramConfig {

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    ReviewEventRepository reviewEventRepository(JdbcTemplate jdbc) {
        return new ReviewEventRepository(jdbc);
    }

    @Bean
    SchedulerProjection schedulerProjection(JdbcTemplate jdbc) {
        return new SchedulerProjection(jdbc);
    }

    @Bean
    ConceptCandidateRepository conceptCandidateRepository(JdbcTemplate jdbc) {
        return new ConceptCandidateRepository(jdbc);
    }

    @Bean
    CandidateVectorRepository candidateVectorRepository(JdbcTemplate jdbc) {
        return new CandidateVectorRepository(jdbc);
    }

    @Bean
    EmbeddingProvider embeddingProvider() {
        return new OpenAiEmbeddingProvider();
    }

    // Stub extractor for bootRun — ingest via API not needed for ENG-8b local testing.
    // Replace with ClaudeExtractor when wiring the full ingest endpoint (ENG-10+).
    @Bean
    Extractor extractor() {
        return doc -> { throw new UnsupportedOperationException("ingest not wired in bootRun yet"); };
    }

    @Bean
    CandidateIngestionService candidateIngestionService(Extractor extractor,
                                                        ConceptCandidateRepository repo,
                                                        EmbeddingProvider embedder) {
        return new CandidateIngestionService(extractor, repo, embedder);
    }

    @Bean
    RetrievabilityEngine fsrs() {
        return new Fsrs();
    }

    @Bean
    ClozeGenerator clozeGenerator() {
        return new ClozeGenerator();
    }

    @Bean
    ReviewService reviewService(ConceptCandidateRepository ccRepo,
                                ReviewEventRepository eventRepo,
                                SchedulerProjection projection,
                                RetrievabilityEngine engine,
                                ClozeGenerator clozeGenerator,
                                ActivatedCardRepository cardRepo) {
        return new ReviewService(ccRepo, eventRepo, projection, engine, clozeGenerator, cardRepo);
    }

    @Bean
    DashboardRepository dashboardRepository(JdbcTemplate jdbc) {
        return new DashboardRepository(jdbc);
    }

    @Bean
    DashboardService dashboardService(DashboardRepository dashboardRepository, RetrievabilityEngine engine) {
        return new DashboardService(dashboardRepository, engine);
    }

    @Bean
    ClaudeClient claudeClient() {
        return new AnthropicClaudeClient();
    }

    @Bean
    Professor professor(ClaudeClient claudeClient) {
        return new Professor(claudeClient);
    }

    @Bean
    Distractor distractor(ClaudeClient claudeClient) {
        return new Distractor(claudeClient);
    }

    @Bean
    GenerationOrchestrator generationOrchestrator(Professor professor, Distractor distractor) {
        return new GenerationOrchestrator(professor, distractor);
    }

    @Bean
    ActivatedCardRepository activatedCardRepository(JdbcTemplate jdbc) {
        return new ActivatedCardRepository(jdbc);
    }

    @Bean
    ActivationService activationService(ConceptCandidateRepository conceptRepo,
                                        CandidateVectorRepository vectorRepo,
                                        ActivatedCardRepository cardRepo,
                                        GenerationOrchestrator orchestrator) {
        return new ActivationService(conceptRepo, vectorRepo, cardRepo, orchestrator);
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
