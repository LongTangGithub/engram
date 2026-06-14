plugins {
    java
    // Versions may need bumping to current releases — verify before first build.
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.engram"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    // Spring Boot core + JSON (Jackson databind). No web: the spike is a CLI runner.
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Explicit SQL (JdbcTemplate). No ORM — append-only log fights mutable-entity model.
    // DataSource autoconfig excluded in EngramSpikeApplication so bootRun still works without a DB.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Tests — plain JUnit 5 + embedded-postgres (real PG binary, no Docker required).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")
}

tasks.withType<Test> { useJUnitPlatform() }
