package com.engram;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Shared pgvector-enabled Postgres container for all integration tests.
 *
 * Single container per JVM; isolation comes from Flyway clean+migrate in each @BeforeEach.
 *
 * Docker Desktop on macOS: the default socket returns HTTP 400 on versioned API paths — breaking
 * docker-java. build.gradle.kts injects DOCKER_HOST=docker.raw.sock as a test-task env var so
 * Testcontainers connects to the real engine. ~/.testcontainers.properties carries the same fix
 * for IDE test runs. CI (Linux) inherits DOCKER_HOST from the environment automatically.
 */
public class TestDatabase {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("engram_test")
                    .withUsername("engram")
                    .withPassword("engram");

    static {
        CONTAINER.start();
    }

    public static DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(CONTAINER.getJdbcUrl());
        ds.setUsername(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        return ds;
    }
}
