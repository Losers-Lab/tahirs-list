# Tahir's List — Backend

Kotlin backend for Tahir's List — **Ktor 3** (coroutines, CIO engine), PostgreSQL/PostGIS,
Exposed for data access, Flyway for migrations, Koin for dependency injection.

**Implemented so far:** M1 skeleton + TDD harness, **Create Account** (sc-39) —
email-uniqueness check, password-strength validation, Argon2id hashing, and the
`users` Flyway migration (6 MVP roles, default `USER`) — and **Log In** (sc-40):
credential verification, short RS256 JWT access token with RBAC role claims, and
rotating hashed refresh tokens (`refresh_tokens` Flyway migration, ~30-day
lifetime). See `openapi/v1.json` for the current contract.

## Stack

- **JDK 21 LTS** (Temurin), **Kotlin 2.4** (K2), **Gradle 9.8** (Kotlin DSL, configuration cache)
- **Ktor 3.6** (CIO engine, coroutines) — routing, content negotiation, status pages,
  authentication, rate limiting
- **Koin 4.2** — explicit Kotlin DI modules (replaces the old Spring `@Configuration` classes)
- **Exposed 1.6** — suspend transactions; raw SQL escape hatch for the PostGIS queries
- **kotlinx.serialization** for JSON, **kotlinx-datetime** for civil dates,
  **kotlin.time.Instant/Clock** for instants, **kotlin.uuid.Uuid** for ids
- **Flyway 13** + **PostgreSQL 17 / PostGIS 3.4** + **MinIO** (S3-compatible, via the
  **AWS SDK for Kotlin**) through Docker Compose
- **Kotest 6 + MockK + Testcontainers 2** — mandated backend TDD
- Java libraries that have no maintained Kotlin-first equivalent stay behind Kotlin
  ports: Nimbus (JWT signing), BouncyCastle (Argon2id), PostgreSQL JDBC driver,
  Flyway, HikariCP. See `ARCHITECTURE.md` §1.0.

## Module layout (hexagonal)

```
:domain ──► :application ──► adapters (:persistence, :storage-s3,
    :verification-ai, :verification-committee, :web-api) ──► :bootstrap
```

| Module | Role |
|--------|------|
| `:domain` | Pure Kotlin domain core (only `kotlinx-datetime` for civil dates). |
| `:application` | Use cases / ports (in & out), framework-free, coroutine-based. |
| `:persistence` | PostgreSQL/PostGIS via Exposed + a Flyway migration facade. |
| `:storage-s3` | S3-compatible storage adapter (MinIO now, R2 later), AWS SDK for Kotlin. |
| `:verification-ai` / `:verification-committee` | Verification adapters (hosted AI via Ktor client + human review). |
| `:web-api` | Ktor route modules, DTOs, JWT authentication, status pages, rate limiting. |
| `:bootstrap` | Composition root: Koin modules, config, Ktor entrypoint (`ApplicationKt`). |

## Local development

The host has **no language toolchains** (AGENTS.md): everything runs in containers.

```bash
# 1. Backing services (PostGIS + MinIO + bucket provisioning)
docker compose up -d          # postgis:5432, minio api:9000 console:9001

# 2. Run the live dev stack (builds and runs the app in a container)
./scripts/backend-live.sh     # http://localhost:8080  /v1/health

# 3. Full build + tests, in the build container
./scripts/backend-test.sh
```

Configuration is environment-variable based (no profiles). Defaults target the
local compose stack; override with `APP_DB_URL`, `APP_DB_USER`, `APP_DB_PASSWORD`,
`APP_JWT_ISSUER`, `APP_JWT_ACCESS_TTL_SECONDS`, `APP_JWT_RSA_PRIVATE_KEY_BASE64`,
`APP_COOKIE_SECURE`, `APP_RATELIMIT_*`, `APP_S3_*`, `APP_VERIFICATION_*`,
`APP_SEED_INGEST_*`, `APP_PORT`. See
`bootstrap/src/main/kotlin/com/tahirslist/bootstrap/config/AppConfig.kt`.

The `persistence` smoke test boots a real PostGIS container via Testcontainers
(no local PostGIS required to run it).

## Containerized build (required on machines without a JDK)

```bash
./scripts/backend-test.sh              # full build + tests
./scripts/backend-test.sh test-only    # tests only
```

Both run Gradle in `eclipse-temurin:21-jdk` with the dependency cache in the
`tahirs-list-gradle-test-cache` volume and Testcontainers support via the
Docker socket.

## OpenAPI spec

The Ktor migration removed springdoc. The contract remains the **committed**
`openapi/v1.json` file consumed by the frontend type client and browser
extensions. Runtime generation is not wired yet — a Ktor-native generator (or a
route/spec contract test) is a follow-up; until then, update the committed spec
alongside any contract change.

## Notes for later backend work

- TDD discipline (test-driven-development) applies to all backend work.
- **Docker Desktop / Engine 29**: Testcontainers needs a modern Docker API
  version. The Gradle `Test` tasks pin `api.version=1.44` — see root
  `build.gradle.kts` (testcontainers-java #11212).
- **Suspend by default**: out-ports and use cases are `suspend`; blocking work
  (Argon2 hashing) is wrapped in `Dispatchers.Default` at the use-case boundary.
- **Flyway** is retained as the migration executor only (no maintained
  Kotlin-native migrator); the SQL under `db/migration` is unchanged.
