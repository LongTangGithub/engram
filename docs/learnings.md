# Learnings

Durable patterns, gotchas, and conventions discovered while building Engram. Read this at the
start of every task and follow what's here. This is the project's accumulated muscle memory —
distinct from `progress.md` (live status) and the visible task list (short-lived working memory).

Add an entry when you:

- discover a **pattern** worth reusing,
- hit a **gotcha** worth never hitting again,
- establish a **convention** the codebase should follow,
- or the user corrects a mistake — apply it, log it, and show the new rule before continuing.

Each entry is:

- a concrete *do* or *don't* (not a vague principle),
- one or two sentences, scannable at a glance,
- tagged with the date (`YYYY-MM-DD`) so stale entries can be pruned.

Format: `- (YYYY-MM-DD) <do / don't>. Why: <the pattern, gotcha, or incident that prompted it>.`

---

## Patterns
> Reusable approaches that worked — reach for these first.

- (2026-06-21) LLM client as interface for testability: define `ClaudeClient` as an interface so tests can inject `FakeClaudeClient` that captures every `(tier, system, userText)` call. Asserts on what was passed IN (prompts, call count) — not on what the fake returns. Why: a mock that only checks return values proves nothing about RAG grounding or cost-gate behavior.
- (2026-06-21) Token cost in micros: `cost_micros = round(inputTokens * inputPricePerMTok + outputTokens * outputPricePerMTok)`. This works because USD-per-MTok × tokens / 1_000_000 × 1_000_000_micros/USD = tokens × pricePerMTok. Avoids float precision issues and keeps everything integer arithmetic in the DB.
- (2026-06-21) Generate-once via UNIQUE + ON CONFLICT DO NOTHING: `activated_card` has UNIQUE on `concept_id`. `ActivatedCardRepository.save()` uses `ON CONFLICT (concept_id) DO NOTHING` and returns false on conflict. `ActivationService` checks cache before calling orchestrator (sequential guarantee); if both checks miss concurrently, the conflict loser re-queries the winner's row.

- (2026-06-21) Use Testcontainers with `pgvector/pgvector:pg16` for tests that require pgvector. `io.zonky.test:embedded-postgres` bundles a vanilla Postgres binary without pgvector — `CREATE EXTENSION IF NOT EXISTS vector` fails. `TestDatabase.java` (static singleton `PostgreSQLContainer<?>`, `DriverManagerDataSource`) provides the shared `DataSource`. Isolation: Flyway `clean()+migrate()` in `@BeforeEach`. No docker-compose dependency.
- (2026-06-21) Testcontainers on macOS Docker Desktop — complete fix (3 interlocking issues): (1) `docker.host` in `~/.testcontainers.properties` must point to `docker.raw.sock` (`~/Library/Containers/com.docker.docker/Data/docker.raw.sock`), not `~/.docker/run/docker.sock` (the proxy socket returns HTTP 400 on versioned API paths). (2) docker-java's API version: TC's shaded docker-java uses property key `api.version` (the Java constant `DefaultDockerClientConfig.API_VERSION`), NOT the Docker env var `DOCKER_API_VERSION`. Set via Gradle `systemProperty("api.version", "1.44")` in the test task. (3) Ryuk bind-mount: TC 1.20.6 reads env var `TESTCONTAINERS_RYUK_DISABLED` (the old `ryuk.disabled` properties key is silently ignored in 1.20.x). Set via Gradle `environment("TESTCONTAINERS_RYUK_DISABLED", "true")` — ryuk tries to bind-mount the Docker socket which fails on `docker.raw.sock`. Additionally: Spring Boot 3.3.4 BOM pins `testcontainers:testcontainers` to 1.19.8; override with `resolutionStrategy.eachDependency { useVersion("1.20.6") }` to get docker-java 3.4.1 which handles Docker Desktop 29.x min-API requirement.
- (2026-06-12) ~~Use `embedded-postgres` for DB integration tests that don't need pgvector.~~ **Fully superseded 2026-06-21**: use Testcontainers `pgvector/pgvector:pg16` for all integration tests (see Docker Desktop fix entry above). Keeping embedded-postgres would create two test DB strategies in the same codebase.
- (2026-06-12) Single pure `apply(event, state)` function shared between incremental projection update and replay rebuild. Why: guarantees replay == incremental by construction — no divergence possible if both paths call the same function. Replay order must be deterministic: `ORDER BY occurred_at, seq` where `seq BIGSERIAL` is the tiebreaker within same timestamp.

## Gotchas
> Traps, sharp edges, surprising behavior — things that bit us once.

- (2026-06-21) `argThat(lambda)` in Mockito requires a null-safe lambda. During `when(mock.method(argThat(lambda)))` setup for a second stub on the same method, Mockito evaluates previous stubs' matchers with null (the argThat placeholder value). Always write `argThat(d -> d != null && d.someField().equals(...))` — bare `d.someField()` throws NPE when d is null.
- (2026-06-21) pgvector JDBC: store vectors as `CAST(? AS vector)` where `?` is a string literal `[v1,v2,...,vn]`. Build the string with `StringBuilder` (never `Arrays.toString` — it adds spaces after commas). Use `static String formatVector(float[] v)` in one place and reference it everywhere.
- (2026-06-21) Use HNSW index (not IVFFlat) for pgvector on a table that starts empty or grows incrementally. IVFFlat requires k-means clustering on existing data at index creation time — `CREATE INDEX ... USING ivfflat` on an empty table produces a degenerate index with near-zero recall. HNSW builds correctly on any table size: `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=200)`.
- (2026-06-14) Docker Desktop on macOS binds port 8080 via its backend process (`com.docker.backend`). Always set `server.port=8081` (or any other port) for local Spring Boot dev when Docker Desktop is running. The symptom is a clean `Address already in use: 8080` with no user process holding the port.
- (2026-06-14) Two `@SpringBootApplication` classes in the same Gradle module → `resolveMainClassName` task fails with "multiple main classes found." Fix: add `springBoot { mainClass.set("com.engram.EngramApplication") }` to `build.gradle.kts`. The spike's `EngramSpikeApplication` also needs `app.setWebApplicationType(WebApplicationType.NONE)` so it doesn't start a server when `./gradlew bootRun` is aimed at it directly.
- (2026-06-14) Tailwind 4 is CSS-first: no `tailwind.config.ts` file. Import via `@import "tailwindcss"` in `globals.css`. Custom tokens go in an `@theme inline { ... }` block in the same CSS file. The `tailwind-merge` and `clsx` ecosystem works unchanged.
- (2026-06-14) FSRS-6 has 21 parameters (indices 0–20), not 19. w[20] is the DECAY base (DECAY = -w[20]); FACTOR is derived: `0.9^(1/DECAY) - 1`. Do not hard-code FACTOR as 19/81 (that was FSRS-5). Always re-derive FACTOR from the weights when updating the weight set.
- (2026-06-14) py-fsrs 6.x uses a learning-step state machine (Learning → Review → Relearning states). Our Java engine only ports Review-state math (no steps, no state enum in FsrsState). Short-term stability (w[17..19]) is unused in the engine — it belongs to the scheduler wrapper, not the decay math. This is intentional: ENG-6 wiring only feeds Review-state events.
- (2026-06-14) STABILITY_MIN in py-fsrs 6.3.1 is 0.001 (not 0.1). Verify the constant from the reference source — do not guess clamping bounds.

- (2026-06-12) Flyway 10 (managed by Spring Boot 3.3+) dropped Postgres support from `flyway-core`. Add `org.flywaydb:flyway-database-postgresql` separately or Flyway throws `Unsupported Database: PostgreSQL X.Y`. Version is managed by Spring Boot BOM — no explicit version needed.
- (2026-06-12) `spring-jdbc` standalone (`org.springframework.jdbc:spring-jdbc`) doesn't resolve via the Spring Boot BOM dependency management plugin alone. Use `spring-boot-starter-jdbc` instead; exclude `DataSourceAutoConfiguration` and `FlywayAutoConfiguration` from `@SpringBootApplication` if the app doesn't need a DB at startup (e.g., the spike runner).
- (2026-06-12) Idempotent insert on append-only table must use `ON CONFLICT (...) DO NOTHING` — never `DO UPDATE`. `DO UPDATE` would fire the append-only trigger and raise an exception.
- (2026-06-13) Normalize path separators to `/` at the point of creation, not at the point of comparison. Why: `Path.relativize().toString()` yields `\` on Windows; if sourceRef (the SyncDiff key and future persisted candidate identity) is built with OS-native separators, the same vault synced from Mac and Windows produces different refs → every file reads as removed+add on cross-platform sync. Fix in the adapter; never mask it in the test helper.
- (2026-06-12) The append-only trigger must be ONLY on `review_event`. `concept_scheduler_state` (projection, mutable) and `review_event_answer_payload` (tombstonable) must remain updateable — the tombstone test would fail if the trigger was applied there.

## Conventions
> How this codebase does things — names, structure, style to stay consistent with.

- (2026-06-13) LLM extractors can emit duplicate titles within a single doc. Dedupe by title in the SERVICE before persisting — not in the repo. Without dedupe, `ON CONFLICT DO UPDATE` silently overwrites the earlier row while the summary still counts both, so `candidatesCreated` diverges from the actual DB row count. Use last-wins (LinkedHashMap insertion order) to match the DB's ON CONFLICT behavior.
- (2026-06-13) `Collectors.toMap` throws `IllegalStateException` on duplicate keys. Any `loadPriorHashes`-style query that returns one row per candidate (not one row per doc) can produce duplicates. Use `GROUP BY source_ref` + `MAX(...)` in SQL to derive one hash per ref deterministically, AND add a `(a, b) -> b` merge function to `toMap` as a safety net.
- (2026-06-13) For CHANGED docs in the ingestion loop: delete old candidates first, then re-extract and insert. Never use upsert-only for changed docs — a re-extraction may produce different titles (old titles would survive as stale rows). Pattern: delete-by-doc → extract → upsertAll.

- (2026-06-14) Text masking (cloze generation) must use whole-word boundaries, never substring replace. Use `(?<![\\w-])Pattern.quote(term)(?![\\w-])` — the `[\\w-]` lookarounds treat hyphens as word-connectors so "retrieval" does not match inside "retrieval-practice". Never use `String.indexOf` or bare `Pattern.quote` without boundaries; partial matches silently corrupt prompts on real vault text. For pure-word fallbacks (`split("\\W+")` tokens), `\b` is sufficient.
- (2026-06-14) FSRS due_at must use second precision, not whole-day rounding. `reviewedAt.plusSeconds(Math.round(stability * 86400))` — rounding stability to whole days bakes up to 12 hours of scheduling drift into every interval. The spec says "due_at = reviewedAt + stability days" but that means the real-valued interval, not `round(stability)` days.
- (2026-06-14) openapi-typescript v7+ generates types under `components["schemas"]["TypeName"]`, not as top-level named exports. Never import `ClozeCardResponse` directly from `api-types.ts` — re-export it from `lib/api.ts` as `export type ClozeCardResponse = components["schemas"]["ClozeCardResponse"]`. All generated fields are optional (`?:`) by default; guard with `?? defaultValue` at every use site.
- (2026-06-14) New Spring `@RestController` beans in a package not listed in `scanBasePackages` are silently ignored — no 404, no error, just no route. Add the new package string to `@SpringBootApplication(scanBasePackages = {...})` in `EngramApplication.java` AND wire `@Bean` methods in `EngramConfig` for any manually-wired services. Symptom: `GET /api/dashboard` returns 404 even though the class compiles.
- (2026-06-14) CORS allowedOrigins must include every port the frontend might land on. Next.js grabs 3001 when 3000 is taken; add both to `allowedOrigins` or the dashboard fetch silently fails with a CORS error in the browser.
- (2026-06-21) Canonical embed text for a concept: `title + "\n" + sourceSpan` (or just title if sourceSpan is blank). Defined ONCE in `CandidateIngestionService.toEmbedText(title, sourceSpan)`. Every caller — upsert, backfill, kNN query — must use this exact method to stay in the same vector space. If the text representation ever changes, ALL stored embeddings must be re-embedded (full backfill).
- (2026-06-14) CORS for the review API: configure via `WebMvcConfigurer` `@Bean` in `EngramConfig` — not via `@CrossOrigin` on the controller. Pattern: `registry.addMapping("/api/**").allowedOrigins("http://localhost:3000").allowedMethods("GET","POST","OPTIONS")`. If the frontend port changes (e.g., Next.js grabs 3001 when 3000 is taken), update `allowedOrigins` here.
- (2026-06-12) Production domain types live in `com.engram.<domain>` (e.g., `com.engram.review`). Spike/prototype code lives in `com.engram.spike.*`. When ENG-2 superseded the spike's `ReviewEvent`, the old type was deleted and spike callers updated to import from `com.engram.review`.
- (2026-06-12) Repositories are plain classes with a `JdbcTemplate` constructor arg — not Spring `@Repository` beans. Tests wire them directly from an embedded-postgres `DataSource`. Spring DI wiring comes later when there's a proper app context with a configured DataSource.
- (2026-06-12) Only call `SchedulerProjection.applyEvent()` when `ReviewEventRepository.append()` returned `true`. The log is idempotent (`ON CONFLICT DO NOTHING`), but the projection is not — applying a duplicate event double-counts `reviewCount`. The append-then-apply call site must couple them: `if (repo.append(e)) projection.applyEvent(e);`. Enforce this at the write site when it lands (ENG-6).