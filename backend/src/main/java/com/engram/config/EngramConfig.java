package com.engram.config;

import com.engram.concept.ConceptCandidateRepository;
import com.engram.dashboard.DashboardRepository;
import com.engram.dashboard.DashboardService;
import com.engram.quiz.ClozeGenerator;
import com.engram.quiz.ReviewService;
import com.engram.review.ReviewEventRepository;
import com.engram.review.SchedulerProjection;
import com.engram.scheduler.Fsrs;
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
    Fsrs fsrs() {
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
                                Fsrs fsrs,
                                ClozeGenerator clozeGenerator) {
        return new ReviewService(ccRepo, eventRepo, projection, fsrs, clozeGenerator);
    }

    @Bean
    DashboardRepository dashboardRepository(JdbcTemplate jdbc) {
        return new DashboardRepository(jdbc);
    }

    @Bean
    DashboardService dashboardService(DashboardRepository dashboardRepository, Fsrs fsrs) {
        return new DashboardService(dashboardRepository, fsrs);
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
