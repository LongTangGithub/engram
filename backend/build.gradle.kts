plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.engram"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

// Spring Boot 3.3.4 BOM pins testcontainers:testcontainers to 1.19.8. Override via eachDependency
// (runs after BOM resolution) to get Docker Desktop socket fixes in 1.20.x.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.testcontainers") {
            useVersion("1.20.6")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // On macOS Docker Desktop, ~/.docker/run/docker.sock is a proxy that returns HTTP 400 on
    // versioned API paths (/v1.xx/info) — breaking docker-java / Testcontainers. docker.raw.sock
    // bypasses the proxy and speaks directly to the engine. We detect it here and inject
    // DOCKER_HOST as an env var so it overrides any docker.host in ~/.testcontainers.properties.
    // On CI / Linux, rawSock won't exist and the test JVM inherits the correct DOCKER_HOST from
    // the environment (e.g. GitHub Actions sets it automatically).
    val rawSock = file("${System.getProperty("user.home")}/Library/Containers/com.docker.docker/Data/docker.raw.sock")
    if (rawSock.exists()) {
        environment("DOCKER_HOST", "unix://${rawSock.absolutePath}")
        // docker.raw.sock advertises API 1.54 but docker-java defaults to requesting 1.32,
        // which Docker Desktop 29.x rejects (minimum is 1.40). The shaded docker-java inside
        // testcontainers uses property key "api.version" (constant DefaultDockerClientConfig.API_VERSION),
        // NOT the Docker CLI env var "DOCKER_API_VERSION". Set as JVM system property.
        systemProperty("api.version", "1.44")
        // TC 1.20.6 reads TESTCONTAINERS_RYUK_DISABLED (env var, not the old ryuk.disabled property).
        // Ryuk would otherwise try to bind-mount docker.raw.sock which Docker rejects ("mkdir ... not supported").
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    }
}

springBoot {
    mainClass.set("com.engram.EngramApplication")
}
