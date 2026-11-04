---
type: handoff-document
title: "02 — Architecture: How Tahir's List Is Built"
description: "The ratified stack, hexagonal module layout, repository structure, data model, API surface, and the architecture decisions that are settled versus still open."
tags: [tahirs-list, architecture, backend, frontend, kotlin, ktor, postgis, nextjs]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE — agreed architecture and open decisions"
  - resource: AGENTS.md
    title: "AGENTS — containerized execution and engineering rules"
  - resource: backend/settings.gradle.kts
    title: "Gradle module graph"
  - resource: backend/docker-compose.yml
    title: "Local backing services"
---

# 02 — Architecture

## Status of the stack

**Established.** The backend stack is **ratified** (2026-08-28) and implemented.
Hosting, geo, reviews, verification provider, auth tuning, and SEO were ratified
2026-08-29. The architecture document still carries open product questions (U-14,
U-15) and open flags (certificate expiry/revocation).

> Read `ARCHITECTURE.md` in the repo as the governing document. This handoff
> summarizes it; if they disagree, `ARCHITECTURE.md` wins.

## The stack at a glance

| Layer | Decision |
|-------|----------|
| Backend language | **Kotlin** 2.4 |
| Backend framework | **Ktor 3.6** (coroutines, CIO engine; Kotlin-first rework 2026-11-03) |
| Build | **Gradle 9.8 (Kotlin DSL)**, JDK 21 LTS (Temurin), configuration cache |
| Database | **PostgreSQL 16/17 + PostGIS 3.4** |
| Data access | **Exposed 1.6** (suspend transactions; raw SQL for PostGIS); **Flyway** migrations. JPA/Hibernate and Mongo are explicitly denied. |
| API contract | Committed **OpenAPI 3.1** at `/v1`, validated against Ktor routes; runtime generation is a follow-up |
| Auth | Ktor JWT authentication (Nimbus verification); email/password with **Argon2id**; short **JWT (RS256)** access tokens + **rotating hashed refresh** tokens in HttpOnly cookies; RBAC for 6 roles + anonymous + extension API keys |
| Geo | PostGIS `geography(Point,4326)` + GiST; one query using `ST_DWithin` (filter) and `ST_DistanceSphere` (order); denormalized `listing_search` table; offset paging; **straight-line haversine, miles** |
| Object storage | **MinIO self-hosted** (S3-compatible) for MVP; private bucket, presigned PUT/GET, namespaced keys, versioning + SHA-256 audit. **Cloudflare R2 is the target later**; build against the S3 SDK on a configurable endpoint. |
| Verification | **Hosted multimodal vision API** (Gemini 2.5 Flash, paid tier) behind a `VerificationProvider` port; conservative verdict; **human review mandatory**. Alternative considered: Claude Haiku 4.5. |
| Frontend | **Next.js (TypeScript)**, SSR/SEO; later wrapped in **Capacitor** for mobile. Typed client generated from OpenAPI. |
| Extensions | MV3 thin content-script overlays for Google + Yelp, shared TS core, address matching. In MVP scope; not built. |
| Hosting | **Hetzner VPS** (CX/CAX line, ~US$6–7/mo) self-hosting Postgres+PostGIS, MinIO, the Ktor app image, and the async verification worker. **Ratified but not provisioned.** |
| Testing | **Kotest + MockK + Testcontainers (PostGIS)** + Ktor testApplication; hexagonal modules `:domain → :application → adapters → :bootstrap` |

### Deliberate constraints you must not violate

**Established** from AGENTS.md, founder directives:

- **The host machine has no language toolchains. Do not install any.** No JDK, no
  Gradle, no Node, no npm on the host. **Docker is the only build runtime.**
- Backend build/test: `./scripts/backend-test.sh` (Gradle + tests in
  `eclipse-temurin:21-jdk`); `./scripts/backend-test.sh test-only` for tests only.
- Frontend build/test: `./scripts/frontend-test.sh` (frozen `pnpm install` +
  `svelte-check` + `vitest` + `vite build` in `node:24-slim`); `dev-install`
  allows lockfile drift.
- Dependencies cache in named volumes (`tahirs-list-gradle-test-cache`,
  `tahirs-list-pnpm-store`, `tahirs-list-frontend-node_modules`), never in the
  repo tree.
- New tooling (linters, generators, migration tools) also runs in containers. If
  you reach for `apt`/`pip install -g`/`npm install -g` on the host, stop.

### Testcontainers-in-Docker caveat

The backend build container mounts `/var/run/docker.sock` so Testcontainers can
manage sibling containers. Two settings are baked into the script and must stay:

- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` plus
  `--add-host host.docker.internal:host-gateway`, otherwise sibling containers
  fail with "Connection to 172.17.0.1 refused".
- `TESTCONTAINERS_RYUK_DISABLED=true`, because Ryuk has the same cross-container
  networking problem.

**Security note:** the `docker.sock` mount is host-root-equivalent. It was accepted
deliberately for building this trusted repo. Do not extend that pattern to
running untrusted code.

## Repository layout

**Established.**

```
halal-food-finder/
├── README.md                  product overview + personas
├── ARCHITECTURE.md            agreed architecture + open decisions
├── AGENTS.md                  engineering rules (read before coding)
├── backend/                   Kotlin/Ktor, Gradle multi-module
│   ├── domain/                entities, value objects, domain rules
│   ├── application/           use cases, ports
│   ├── adapters/
│   │   ├── persistence/       Exposed repos + Flyway migrations
│   │   ├── geocoding/         Photon/Google geocoder adapters
│   │   ├── storage-s3/        MinIO/S3 image port
│   │   ├── verification-ai/   hosted vision adapter
│   │   ├── verification-committee/  human review adapter
│   │   └── web-api/           controllers, security, error handling
│   ├── bootstrap/             Ktor composition root (Koin modules + entrypoint)
│   ├── openapi/               committed OpenAPI spec
│   ├── docker-compose.yml     PostGIS + MinIO (+ minio-init)
│   └── settings.gradle.kts    module graph
├── frontend/                  Next.js (TypeScript), App Router
│   └── src/app/               page.tsx routes (9 pages)
├── brand/tahir/               mascot, lockups, icons, BRAND-DECISIONS.md, CHARACTER.md
├── docs/
│   ├── design/                design specs, tokens, per-story design notes
│   ├── qa/                    QA assessment + testing conventions
│   ├── research/              external-services and seed-data briefs
│   ├── reviews/               external-services review
│   ├── security/              auth security review
│   └── handoff/               this bundle
├── scripts/                   backend-test.sh, frontend-test.sh, backend-live.sh,
│                              generate_seed_migration.py
├── sketches/                  brand/mascot exploration
├── dogfood-output/            screenshot and review captures
└── .worktrees/                42 historical git worktrees (cleanup candidate)
```

### Backend module graph (from `settings.gradle.kts`)

**Established.** Physical directories under `adapters/` are registered with
top-level project names so the module graph matches the ratified architecture:

```
:domain → :application → adapters → :bootstrap
```

Registered projects: `:domain`, `:application`, `:persistence`, `:geocoding`,
`:storage-s3`, `:verification-ai`, `:verification-committee`, `:web-api`,
`:bootstrap`.

This is a genuine hexagonal layout: domain and application do not depend on
frameworks; adapters do. Keep it that way. If a change makes `:domain` import
Ktor/Exposed, the architecture has been violated.

## API surface

**Established** from the Ktor route modules in `backend/adapters/web-api`:

| Route module | Responsibility |
|------------|----------------|
| `AuthRoutes` | Signup, login, refresh, logout |
| `MeRoutes` | Current-user identity |
| `ListingRoutes` | Listing read surface, create/update, owner image operations |
| `FavoriteRoutes` | Favorites |
| `VerificationRoutes` | Owner claim + committee queue, approve/deny |
| `HealthRoutes` | Liveness probe (`GET /v1/health`) |

Plus image endpoints on listings (`sc-53`/`sc-54`, merged) and `/v1/health`
for readiness. The OpenAPI spec is committed under `backend/openapi/`; frontend
types are generated from it by the SvelteKit frontend.

## Data model and migrations

**Established.** 20 Flyway migrations in
`backend/adapters/persistence/src/main/resources/db/migration/` (V1–V20). They
tell the story of the product better than any prose:

| Migration | What it introduced |
|-----------|--------------------|
| V1 | users |
| V2 | refresh tokens |
| V3 | refresh-token families |
| V4 | restaurant listings |
| V5 | brands |
| V6 | seed listing schema |
| V7 | seed restaurants |
| V8 | listing_search (denormalized geo/search table) |
| V9 | price + cuisine filters (`sc-43/44`) |
| V10 | rating + distance (`sc-45/72`) |
| V11 | alcohol_served (`sc-118`) |
| V12 | favorites |
| V13 | halal certification reviews |
| V14 | AI consent (`sc-120`) |
| V15 | allow verified status (`sc-73`) |
| V16 | certification metadata (`sc-73`) |
| V17 | hand_cut boolean (`sc-42`) |
| V18 | partial halal (`sc-119`) |
| V19 | delivery service mode (`sc-184`) |
| V20 | structured address (`sc-187`) |

**Caution:** the migration history shows several renumbers and rebases (for
example "Renumber delivery migration to V19", "Renumber partial-halal migration to
V18"). Before adding a migration, check the actual highest applied version in the
target database — do not trust the filename alone. See
[05-operations-and-infrastructure.md](05-operations-and-infrastructure.md).

## Frontend

**Established.** Next.js App Router, TypeScript, 9 routes:

- `/` — home
- `/search` — search/browse with filters
- `/restaurants/[id]` — detail
- `/restaurants/[id]/edit` — owner manage/edit
- `/add-listing` — add a listing
- `/saved` — favorites
- `/verification` — verification flow / VC review surface
- `/login`, `/signup` — auth

Dependencies are deliberately small: `next`, `react`, `react-dom`,
`react-hook-form`, `zod`, `@hookform/resolvers`. No state-management library. The
API client is generated from OpenAPI (`openapi-typescript`).

22 frontend test files cover pages and key components (map preview, mobile tab
bar, favorite button, restaurant photo, review card, API client, auth session,
favorites provider, listing data/schemas, review data, verification queue).

## What is built versus what is only designed

**Established** by reading the code and Kanban ledger.

**Built and merged:**

- Auth end-to-end (signup, login, refresh, logout, me, JWT filter, rate limiting,
  security headers, deny-by-default 401 lockdown, fail-fast config).
- Listings create/read/update, owner edit, owner image add/remove (backend).
- Search with PostGIS geo filtering and filters for hand-cut, price, cuisine,
  rating, distance, pickup/delivery.
- Verification seam: claim with AI consent, hosted vision adapter, conservative
  verdict policy, VC review queue with RBAC, certification metadata on read.
- Favorites backend + frontend.
- MinIO image storage, multi-width variants, responsive srcset.
- Structured address, map preview, partial-halal, alcohol-served.
- Brand: mascot v2, C2 lockup, icons, header/footer.

**Designed or scaffolded but not complete:**

- Owner image management **frontend** (the blocked card).
- Browser extensions (no code found).
- Production hosting (no server provisioned).
- Dark theme, Tahir loading animation, pose sheet, sponsor slot (brand backlog).

**Do not assume:** that every design document in `docs/design/` is implemented.
Some are design intent only. Check the Kanban ledger and the code before telling
anyone a feature exists.

## Architecture decisions still open

**Unresolved.** From `ARCHITECTURE.md` Part 2:

- **U-14 Partial-halal semantics** — in MVP scope; exact modeling open.
- **U-15 Alcohol-served attribute** — in MVP scope; field/filter semantics open.
- **Certification expiry/revocation** — display and badge behavior open.
- **Kosher listings** — unresolved.
- **Beyond restaurants (meat shops, etc.)** — unresolved.

The stack-level decisions are all closed. The open items are product-shaped. See
[08-roadmap-and-open-questions.md](08-roadmap-and-open-questions.md).

## Cost posture (why the architecture looks like this)

**Established** from `ARCHITECTURE.md` and the external-services brief. This
matters because cost was a founder concern and drove several choices:

- Self-hosted Hetzner CX-class VPS ~$6–7/mo instead of managed Kubernetes/EKS.
- PostGIS self-hosted so a vendor tier cannot drop spatial support.
- MinIO now ($0) with Cloudflare R2 as the later target (free egress).
- Google Places Autocomplete + Geocoding only, server-side, within free caps
  (10k/month each) → ~$0 at MVP.
- Google Place Details Enterprise fields + Yelp Fusion free tier, cached 24h.
- Gemini 2.5 Flash paid tier at ~$0.0002–0.0005 per verification.
- Estimated M1 run-rate ≈ **$8–10/month**.

**Founder-directed cost-hardening epic (#196)** adds five traps to design against:
zero-egress storage (R2/B2), downscale certificate images before hosted AI,
a geocode cache table, quantized search coordinates + cache headers, and a pinned
JVM footprint. Four are open stories (#197–#200); the S3 endpoint override is
already designed in.
