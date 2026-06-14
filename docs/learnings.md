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

- (2026-06-12) Use `embedded-postgres` (`io.zonky.test:embedded-postgres`) for DB integration tests instead of Testcontainers. Why: Docker Desktop's JVM socket client (docker-java) gets a stub 400 from the Docker Desktop gateway socket on macOS even when `docker run` works fine via the CLI. Embedded-postgres needs no Docker, boots in ~1s, and all Postgres features (triggers, ON CONFLICT, bytea) work identically.
- (2026-06-12) Single pure `apply(event, state)` function shared between incremental projection update and replay rebuild. Why: guarantees replay == incremental by construction — no divergence possible if both paths call the same function. Replay order must be deterministic: `ORDER BY occurred_at, seq` where `seq BIGSERIAL` is the tiebreaker within same timestamp.

## Gotchas
> Traps, sharp edges, surprising behavior — things that bit us once.

- (2026-06-12) Flyway 10 (managed by Spring Boot 3.3+) dropped Postgres support from `flyway-core`. Add `org.flywaydb:flyway-database-postgresql` separately or Flyway throws `Unsupported Database: PostgreSQL X.Y`. Version is managed by Spring Boot BOM — no explicit version needed.
- (2026-06-12) `spring-jdbc` standalone (`org.springframework.jdbc:spring-jdbc`) doesn't resolve via the Spring Boot BOM dependency management plugin alone. Use `spring-boot-starter-jdbc` instead; exclude `DataSourceAutoConfiguration` and `FlywayAutoConfiguration` from `@SpringBootApplication` if the app doesn't need a DB at startup (e.g., the spike runner).
- (2026-06-12) Idempotent insert on append-only table must use `ON CONFLICT (...) DO NOTHING` — never `DO UPDATE`. `DO UPDATE` would fire the append-only trigger and raise an exception.
- (2026-06-12) The append-only trigger must be ONLY on `review_event`. `concept_scheduler_state` (projection, mutable) and `review_event_answer_payload` (tombstonable) must remain updateable — the tombstone test would fail if the trigger was applied there.

## Conventions
> How this codebase does things — names, structure, style to stay consistent with.

- (2026-06-12) Production domain types live in `com.engram.<domain>` (e.g., `com.engram.review`). Spike/prototype code lives in `com.engram.spike.*`. When ENG-2 superseded the spike's `ReviewEvent`, the old type was deleted and spike callers updated to import from `com.engram.review`.
- (2026-06-12) Repositories are plain classes with a `JdbcTemplate` constructor arg — not Spring `@Repository` beans. Tests wire them directly from an embedded-postgres `DataSource`. Spring DI wiring comes later when there's a proper app context with a configured DataSource.
- (2026-06-12) Only call `SchedulerProjection.applyEvent()` when `ReviewEventRepository.append()` returned `true`. The log is idempotent (`ON CONFLICT DO NOTHING`), but the projection is not — applying a duplicate event double-counts `reviewCount`. The append-then-apply call site must couple them: `if (repo.append(e)) projection.applyEvent(e);`. Enforce this at the write site when it lands (ENG-6).