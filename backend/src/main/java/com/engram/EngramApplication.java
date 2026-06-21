package com.engram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main web application entry point (ENG-6+).
 * Explicitly scans only production packages — excludes com.engram.spike so that
 * spike @Component beans (ClaudeClient, CostLog, etc.) are never loaded and their
 * ANTHROPIC_API_KEY requirement doesn't block startup.
 */
@SpringBootApplication(scanBasePackages = {
        "com.engram.config",
        "com.engram.api",
        "com.engram.dashboard",
        "com.engram.review",
        "com.engram.concept",
        "com.engram.embedding",
        "com.engram.ingest",
        "com.engram.scheduler",
        "com.engram.quiz"
})
public class EngramApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngramApplication.class, args);
    }
}
