# ARCHITECTURE.md — Tahir's List

This document describes the architecture of Tahir's List. It is split into
two distinct parts:

1. **Agreed** — the domain model and engineering process we have actually agreed
   upon (derived from the Shortcut Product Requirements Doc and Development
   Process doc).
2. **Unresolved** — technical decisions that are **not yet agreed**. These are
   deliberately left open and marked clearly; **do not treat them as decided.**

> Status: **Active.** Milestone 1 (auth + scaffold vertical slices) is
> implemented and merged; this document tracks the agreed architecture and the
> decisions still open. Hosting, geo, reviews, verification provider, auth
> tuning, and SEO were ratified 2026-08-29 (see §1.0); partial-halal and
> alcohol-served added to MVP scope.
>
> **2026-11-03 — Kotlin-first backend rework (founder directive).** Spring Boot
> is superseded by **Ktor 3** (coroutines, CIO engine), Spring Data JDBC /
> `JdbcTemplate` by **Exposed**, and Java-first patterns are re-evaluated through
> a Kotlin lens. The hexagonal `:domain`/`:application` ports made the swap
> adapter-local. Implementation is in progress on `feature/ktor-migration`.
> The guiding principle is *prefer the Kotlin-native option where it is sane*;
> Java-first libraries with no maintained Kotlin equivalent (JWT/Nimbus,
> Argon2/BouncyCastle, JDBC driver, Flyway execution) remain behind Kotlin
> facades. This is a lens, not a purity rule.

---

## Part 1 — Agreed architecture

### 1.0 Resolved technical decisions (ratified by founder)

The following were explicitly agreed with the founder. Anything not listed here
remains **unresolved** (see Part 2).

| Decision | Agreed | Note |
|----------|--------|------|
| **Frontend architecture** | **SvelteKit 3** (Svelte 5 runes, TypeScript, Vite 8/Rolldown) responsive web (SSR/SEO), later wrapped in **Capacitor** to also ship as a mobile app | Single codebase for website + app; typed client generated from the OpenAPI spec. **Re-ratified 2026-11-04**, replacing Next.js 16: founder directive that speed (build, test, runtime) and lowest container memory are paramount pre-production. Tailwind v4 `@theme` tokens + shadcn-svelte (bits-ui) for UI. Next.js was the 2026-08-28 decision. |
| **Browser extensions (Google + Yelp)** | **In MVP scope** | MV3 thin content-script overlays, shared TS core, address matching; resolved 2026-08-28. |
| **Distance-filter UX** | **Preset radius chips** (ARIA radio-group: 1/5/10/25 mi + "Any distance"), pinned "Near \<place\>" center pill | Fatima (design) recommendation, accepted; resolved 2026-08-28. |
| **Distance semantics** | **Straight-line (haversine)** distance; radius in miles | Founder decision; backend API contract: center + radius query, distance per result. |
| **Geo/search entry UX** | One persistent search bar (Google Maps autocomplete + Current-Location icon/≥44px target on mobile) pinning a search center | Fatima recommendation, accepted. |
| **Filter bar system** | Hero chip row (cutting method — flagship — + top cuisines + distance) + a "+ Filters" sheet (mobile) / popover (desktop) for the tail (price, rating, full cuisine, distance-advanced); cuisine defaults OR with "Advanced" → Any(OR)/All(AND) toggle | Fatima recommendation, accepted; PRD OR default honored. |
| **Verification trust language** | Layer 1: Verified green checkmark badge + posted certification asset (tooltip "Halal certification reviewed & approved by our committee"); Layer 2: neutral "Unverified" tag (never error-red); Layer 3: hand-cut/machine-cut via icon+text tag, colorblind-safe, never color-only | **Verified = green checkmark** (founder); "Unverified" copy + certification expiry display remain open product flags. |
| **Design system / tokens** | Build a **fresh, clean design system from first principles** (mobile-first, trustworthy, accessible), anchored on the agreed verification-trust language; **Figma is not authoritative** | Founder: the existing Figma is an old generic draft and not sacrosanct. Figma-token extraction is **optional**; tokens defined fresh (colors w/ AA contrast, 4px spacing, radius, rem type scale, focus/ring a11y tokens, semantic tokens like `badge-verified`) and wired via Tailwind v4 `@theme`. Brand palette to be confirmed. |
| **Backend language** | **Kotlin** | Founder decision; strong Java background, favourite language Kotlin. |
| **Backend framework** | **Ktor 3.6+** (coroutines; **CIO** engine) | **Founder directive 2026-11-03**: supersedes Spring Boot 3.3. The hexagonal `:domain`/`:application` ports confined the swap to adapters + composition root. |
| **Database & data access** | **PostgreSQL 16/17 + PostGIS**; **Exposed 1.6** (suspend transactions; raw-SQL escape hatch for `ST_DWithin` / `ST_DistanceSphere`); **Flyway** retained as migration executor behind a thin Kotlin facade | Superseded 2026-11-03. Research found no maintained Kotlin-native migration tool; Flyway's SQL migrations carry over unchanged. DENIED: JPA/Hibernate, Mongo. |
| **API contract** | **Committed OpenAPI 3.1 `/v1`** spec remains the contract, validated against Ktor routes in CI | springdoc (Spring-only) superseded 2026-11-03; frontend typed client + extensions consume the committed spec. |
| **Authn/Authz** | **Ktor `Authentication` + JWT** (RS256 verification via **Nimbus** inside the provider); email/password (**Argon2id** via **BouncyCastle** behind the `PasswordHasher` port); short **JWT (RS256)** access + **rotating hashed refresh**; **RBAC** for 6 roles + anonymous + extension API-keys | Same ratified security semantics (deny-by-default, generic 401s, 60 s skew, no revocation list). Nimbus/BouncyCastle stay behind Kotlin ports. |
| **Geo/search** | **PostGIS `geography(Point,4326)` + GiST**; one query: `ST_DWithin` (filter) + `ST_DistanceSphere` (order); denormalized `listing_search` table; offset paging | Straight-line/miles semantics (founder). |
| **Image storage** | **MinIO self-hosted** (S3-compatible, **$0**, runs in Docker alongside the app) for MVP — private bucket, presigned PUT/GET, namespaced keys, versioning + content SHA-256 audit. **Cloudflare R2 is the target for the future** | Founder decision: MinIO now (fully $0, self-operated), **R2 later** (free tier + managed + zero egress). Build against the **S3 SDK on a configurable endpoint** so R2 is a config change, not code — keep all storage access behind one internal interface and avoid AWS-only features. |
| **Verification (hosted AI)** | **Hosted/API multimodal AI (vision)** judges halal-certificate image → conservative verdict + **lightweight human review/correction loop**; **no in-house ML model/training pipeline** | Founded pivot + clarification: "committee" = AI verdict + human spot-check/correct (NOT a people board, NOT training). Pluggable `VerificationProvider` port, default = HostedVisionAdapter. Details: state machine SUBMITTED→AI_REVIEW→AI_SUGGESTED→HUMAN_REVIEW→APPROVED/DENIED (+REVERSED); conservative VerificationSuggestion contract; privacy/security flags (provider terms, PII redaction, retention). See unresolved U-11. |
| **Testing/TDD** | **Kotest + MockK + Testcontainers** (PostGIS) + **Ktor `testApplication`**; hexagonal modules `:domain → :application → adapters → :bootstrap` | Supports mandated backend TDD; no Spring context anywhere in the test suite. |
| **Build/ops baseline** | **Gradle (Kotlin DSL) + configuration cache**, **JDK 21 LTS** (Temurin), **Kotlin 2.4.x** (K2), **Ktor CIO** runtime, Postgres+PostGIS; runtime image via a jlink-tuned JVM | Containerized Gradle build unchanged (`scripts/backend-test.sh`). Kotlin-first lens applied to build/test speed as well as code. |
| **Hosting / deploy (U-08)** | **Hetzner VPS (CX class, ~US$6–7/mo, e.g. CX23 2 vCPU/4GB/40GB) self-hosting** Postgres 16/17 + PostGIS, MinIO, the Ktor app image, and the async verification worker on one always-on box | Ratified by founder 2026-08-29. Cheapest reliable floor — self-host has full extension control so PostGIS can't be dropped by a vendor tier. **Neon (managed Postgres, PostGIS native, free/scale-to-zero) is the $0 zero-ops alternative.** ⚠ Use Hetzner's cost-optimized CX/CAX line, NOT the CPX line (prices raised 2026). Migration is config-cheap: OCI images + env-var connection strings; DB move = pg_dump/restore + one JDBC URL (<1 day). Avoid provider-specific DB lock-in. EKS is NOT an MVP option (~$60–300/mo). |
| **Maps / geo (U-09)** | **Google Places API for Autocomplete + Geocoding only**, held **server-side** (no Maps SDK / heavy rendering — PostGIS does the radius match) | Since 2025-03-01 Google replaced the $200/mo pool with per-SKU free caps: **Autocomplete free 10k/mo, Geocoding free 10k/mo** → ~$0 at MVP (card on file, pay only past caps). OSM (Photon+Nominatim) is the $0-no-card fallback (weaker UX, no SLA). Server-side key → no client exposure. |
| **Reviews / ratings (U-10)** | **Official APIs + server-side keys + 24h backend cache** (1 restaurant view = 1 API call, repeats hit cache at $0): **Google Place Details (Enterprise fields)** = rating + user_ratings_total + reviews ($20/1k, 1k free/mo → $0); **Yelp Fusion free tier** (~500/day) = rating + review_count | MVP shows rating + count + "Read on [Google/Yelp]" link; **Yelp full review-snippet text deferred to paid Enhanced tier (~$7.99–9.99/1k)** until revenue. **No scraping** (ToS/DMCA risk). Fallback for listings w/o linked Google/Yelp: owner/user-entered rating + external links. ~$0/mo. |
| **Verification provider (U-11)** | **Google Gemini 2.5 Flash (PAID API tier)** as the default `HostedVisionAdapter`/`VerificationProvider`; ~$0.0002–0.0005/verification, 1–4s latency, strong document understanding | Ratified 2026-08-29. **Never the free AI Studio tier** (uses data to improve Google products, no retention controls). Non-Vertex Gemini API retains prompts 55 days for abuse monitoring. **Alternative: Claude Haiku 4.5** — cleanest default data posture (no retention/training by default) if guarantees outweigh cost. **Human-in-the-loop is MANDATORY** (both Google & OpenAI require human supervision for high-stakes automated decisions): AI-suggests → VC-approves, never ship AI alone. **Upload hygiene regardless of provider:** downscale ~1024px, strip EXIF, upload cert-only image (not proof-of-ownership composites). Do-not-use: xAI/Grok. |
| **Verification consent (new)** | **Owner must explicitly consent to AI-based verification before uploading** a certificate for verification | Founder requirement 2026-08-29. A consent step/checkbox on the verification upload flow; recorded with the submission. |
| **SEO / public pages (U-13)** | **Public listing/view pages crawlable (SEO) from day one**; owner/dashboard behind auth | Fits listing-first product + SvelteKit SSR at no extra cost; protects future SEO asset. |
| **Auth tuning (U-12)** | **VC/IC as roles on existing accounts** (not separate accounts); standard Argon2id params; extension API-keys rotated on a schedule; refresh-token lifetime ~30 days | Defaults ratified by founder 2026-08-29. |

> **Backend stack is RATIFIED** (revised 2026-11-03): Kotlin + **Ktor 3** (CIO,
> coroutines) + PostgreSQL/PostGIS (**Exposed**; Flyway execution) + OpenAPI 3.1 +
> **Ktor JWT** auth/RBAC + PostGIS geo + S3 images + hosted-AI verification.
> Spring Boot, Spring Security, springdoc and Spring Data/`JdbcTemplate` are
> superseded. Deploy target and a few minor flags remain open (see Part 2).
> **Kotlin-first lens:** prefer the Kotlin-native option where it is sane; Java
> libraries with no maintained Kotlin equivalent stay behind Kotlin facades (e.g.
> Nimbus, BouncyCastle, the PostgreSQL JDBC driver, Flyway execution). This is a
> preference, not a CI purity rule.

### 1.1 Core product model

From the PRD's objective and high-level user stories:

- Users search for halal restaurants by **location** (with current-location
  opt-in) and filter results by **hand-cut** (an extra on/off switch — there is
  no machine-cut concept), **price**, **cuisine** (AND/OR chaining, default OR),
  **rating**, and **distance**.
- Restaurant listings carry halal-relevant attributes: an **alcohol-served**
  flag (whether the establishment serves alcohol) and, **in MVP scope**,
  support for **partial-halal** restaurants (whole-restaurant vs partial
  status). These are related trust/decision attributes and likely filters.
- Restaurant listings support a **verification tag**: a listing starts
  **unverified**; a restaurant owner can "claim" it and, once verified, gains a
  **Verified** status and control over the listing.
- Verification is assisted by an **image-recognition model** that analyzes a
  halal certificate, with a human **Verification Committee** reviewing in the
  early stages.
- Users can **view** a restaurant (address, phone, hours, photo, Google/Yelp
  reviews, and posted certification for verified listings), **favorite**
  restaurants, submit **crowd-sourced edits**, add images, create accounts, and
  report issues.
- **Browser extensions** for Google and Yelp surface the platform's
  listing/verification status on those sites, keyed by **address** matching.

### 1.2 Domain model (agreed — from the PRD PlantUML)

The PRD defines the following domain model. This is the agreed conceptual
architecture; it is **independent of any technology stack**.

- **User** { email, password }
  - **RestaurantOwner** (is-a User)
    - **VerifiedRestaurantOwner** (is-a RestaurantOwner)
- **Restaurant** — has 1..* **HalalCertification**
- **HalalCertification** — verified by **VerificationCommittee** (1 committee ↔
  0..* certifications)
- **RestaurantListing** — represents 1 Restaurant
  - **VerifiedRestaurantListing** (is-a RestaurantListing) — modified by
    VerifiedRestaurantOwner
  - **UnverifiedRestaurantListing** (is-a RestaurantListing) — created/modified
    by User; sends restaurant edits to VerificationCommittee
- **RestaurantListings** — contains 0..* RestaurantListing
- **Filter** { criteria } — used by **SearchResult** (0..* filters)
- **SearchResult** — filters from RestaurantListings

Committees: **VerificationCommittee** (verifications, edits) and
**IssuesCommittee** (user-reported issues).

### 1.3 Personas & states

U = User (not logged in / logged in), RO = Restaurant Owner,
VRO = Verified Restaurant Owner, VC = Verification Committee,
IC = Issues Committee, ET = Extension Trigger. (Full table in README.md.)

### 1.4 Engineering process (agreed — from Shortcut "Development Process" doc)

Per feature, in order:

1. Develop User Story
2. Develop Use Case
3. Update Use Case Diagram
4. **TDD for the backend server**
5. Front-end development

Implications for the architecture:

- **Backend is test-driven.** Tests are written first and are a first-class
  concern.
- Requirements and use cases are authored in Shortcut **before** implementation
  begins on a substantial feature.

### 1.5 External services referenced in the PRD

These services appear in the PRD/use cases as integrations. Agreed choices are
noted; genuinely open items are flagged in Part 2.

- Google Maps API — location search/autocomplete and current location (agreed as
  the geo entry; provider/billing details open, see U-09).
- Google Business / Yelp — ratings and reviews for the View Restaurant use case
  (integration approach open, see U-10).
- Verification image analysis — replaced by the **hosted-AI vision API** decision
  (see 1.0 "Verification (hosted AI)" and U-11). The PRD's "image-recognition
  model" is no longer an in-house model we train.

---

## Part 2 — Unresolved decisions (NOT yet agreed)

The following are **open decisions**. Do not assume any of them is settled.
They need to be resolved (with input from the relevant specialists and the
founder) before implementation of substantial features. Until resolved, treat
them as **unknowns**, not as a chosen architecture.

> The backend stack (U-01…U-07) and hosting/geo/reviews/verification/auth/SEO
> (U-08…U-13) are now **resolved** — see the Agreed section 1.0. Remaining open
> items below.

| ID | Decision | Notes / open questions |
|----|----------|------------------------|
| U-14 | **Partial-halal semantics** | **Confirmed IN MVP scope by founder (2026-08-29)** — the platform should handle partial-halal restaurants thoughtfully (not deferred). Exact modeling still to design: how the verification tag relates to whole-restaurant vs partial status. See product questions below. |
| U-15 | **Alcohol-served attribute** | **New founder requirement (2026-08-29):** whether alcohol is served in the establishment is a listing attribute and likely a filter — an establishment serving alcohol is halal-relevant. Field + filter semantics to be designed. |

### Open product/requirements questions (from PRD "Open Questions" & "Edge Cases")

These are **product** questions, not architecture, but they can constrain the
architecture. They remain unresolved in Shortcut:

- Should **kosher** restaurants be listed?
- Should the platform broaden beyond restaurants (e.g. meat shops)?
- How to model **partial-halal** restaurants / partially halal menus — now **in
  MVP scope** (founder 2026-08-29). The verification tag currently implies
  whole-restaurant status; partial-halal needs its own representation (field /
  tag / filter semantics). Related: the new **alcohol-served** attribute (U-15).
- What happens when a halal **certification expires or is revoked** after
  verification? (Show expiry date; revocation handling remains an open flag —
  founder 2026-08-29.)
- (Resolved in PRD as **deferred**): menu-item entries and analytics
  date-range customizability.

---

## Conventions

- This document should be updated **only** when an architecture decision is
  actually agreed (recorded in Shortcut and/or confirmed by the founder /
  engineering manager). Move items from Part 2 to Part 1 as they are resolved,
  with a note of when and where they were agreed.
- Mark unresolved items explicitly; never silently assume them.
