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
}

tasks.withType<Test> { useJUnitPlatform() }
