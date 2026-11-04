# Tahir's List — Complete Handoff (single-file read view)

> Generated 2026-11-02 from the OKF-style bundle in this directory. The split files are canonical; this is a convenience view for reading or attaching in one pass. If they diverge, the split files win.

---


<!-- ===== index.md ===== -->

---
type: knowledge-bundle-index
title: "Tahir's List — Engineering Handoff Bundle"
description: "Entry point and reading order for the complete handoff of Tahir's List (Halal Food Finder): product vision, architecture, development process, team, operations, work ledger, current state, roadmap, and references."
tags: [tahirs-list, halal-food-finder, handoff, index, knowledge-bundle]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
repo: .
remote: https://github.com/Losers-Lab/tahirs-list
sources:
  - resource: README.md
    title: "README — product overview"
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE — agreed architecture and open decisions"
  - resource: AGENTS.md
    title: "AGENTS — engineering rules and context"
  - resource: app.shortcut.com/halal-food-finder
    title: "Shortcut workspace — product source of truth"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Hermes Kanban — execution queue and work history"
---

# Tahir's List — Engineering Handoff Bundle

This bundle is the handoff from Adnan (Product & Engineering Manager) to whoever
takes over Tahir's List. It is written to be read **synchronously**: start at the
top, follow the order, and stop when you have what you need. Every document is
plain markdown with YAML frontmatter so it diffs, reviews, and versions like code.

It is deliberately **not** a diary. It records what is *true now*, what was
*decided*, what was *built*, and what is *still unknown* — separated, as the
project's own rules require.

## Reading order

| # | Document | Read this when |
|---|----------|----------------|
| 00 | [Executive handoff](00-executive-handoff.md) | **Start here.** The honest state and the first five things to do. |
| 01 | [Product vision](01-product-vision.md) | You need to know what we are building and for whom. |
| 02 | [Architecture](02-architecture.md) | You need to know how the system is put together. |
| 03 | [Development process](03-development-process.md) | You are about to write, review, or merge code. |
| 04 | [Team and roles](04-team-and-roles.md) | You need to know who owns what (and what to stop doing). |
| 05 | [Operations and infrastructure](05-operations-and-infrastructure.md) | You are about to run, deploy, or debug the stack. |
| 06 | [Work ledger](06-work-ledger.md) | You need the factual record of what was built and what is left. |
| 07 | [Current state and risks](07-current-state-and-risks.md) | You need the audit: what is broken, stale, or unverified. |
| 08 | [Roadmap and open questions](08-roadmap-and-open-questions.md) | You are planning the next sprint or need founder decisions. |
| 09 | [References and glossary](09-references-and-glossary.md) | You need IDs, commands, file pointers, or terminology. |
| 10 | [Fresh machine setup](10-fresh-machine-setup.md) | **You are on a new computer.** Docker from zero, services, backend, frontend, smoke tests, troubleshooting. |

## If you read only one thing

Read [00-executive-handoff.md](00-executive-handoff.md). It contains the state of
the system as of 2026-11-02, including the fact that the preview frontend was down
and the pipeline had been idle since 2026-09-09.

**On a new computer, read [10-fresh-machine-setup.md](10-fresh-machine-setup.md)
first.** It is the only document that assumes nothing about your machine. Docs 05
and 06 describe the original host and its local automation; they are context, not
setup instructions.

## The three-sentence version

Tahir's List is a listing-first directory that helps Muslims find halal food,
differentiated by a hand-cut filter and a formal verification process for
restaurant halal certification. The backend (Kotlin/Ktor 3 + PostgreSQL/PostGIS,
hexagonal modules, TDD) and frontend (Next.js/TypeScript) are substantially built,
with 115 completed work items across auth, listings, search/filters, verification,
images, favorites, and owner tools. The project went dormant around 2026-09-09
with one blocked frontend card and a broken preview deployment; everything after
that date in this bundle is diagnosis, not development.

## Source-of-truth hierarchy

When documents disagree, this order wins:

1. **Shortcut** (`app.shortcut.com/halal-food-finder`) — product requirements,
   PRD, use cases, epics, stories, iterations, priorities. Product truth.
2. **This repository** — implementation truth. `ARCHITECTURE.md` and `AGENTS.md`
   are the governing technical documents; this handoff bundle is subordinate to
   them and should be corrected if it drifts.
3. **Hermes Kanban** (`~/.hermes/kanban/boards/halal-food-finder/`) — execution
   truth on the original host: what was dispatched, by whom, and what actually
   happened. **Does not travel with the repository.**
4. **This bundle** — synthesis and pointers. Useful, but not authoritative on
   product or code.

## Provenance and confidence

Every factual claim here is tagged in prose as one of:

- **Established** — read directly from code, config, database, or a primary doc.
- **Observed** — seen in a live system check at the time of writing.
- **Inferred** — my reasoning from evidence; may be wrong.
- **Unresolved** — genuinely unknown; needs a founder or specialist decision.

I have not marked every sentence, but the risky claims are marked explicitly.
Where I am unsure, I say so. That is the point of the exercise.

## Status of this bundle

Written 2026-11-02 while the system was in the state described in
[07](07-current-state-and-risks.md). It is **committed on `main`** in the
repository. The "working tree" and "current branch" details in doc 05 describe
the original host at handoff time, not your checkout.

## Portability

This bundle travels with the repository, and the product, architecture, process,
and roadmap documents apply anywhere. Two things do **not** travel: the Hermes
runtime (Kanban database, cron jobs, agent personas) and the Cloudflare tunnel
token. [10-fresh-machine-setup.md](10-fresh-machine-setup.md) lists exactly what
is missing and how to replace it.

---

<!-- ===== 00-executive-handoff.md ===== -->

---
type: handoff-document
title: "00 — Executive Handoff"
description: "Adnan's direct handoff: why the async model failed, the honest state of Tahir's List as of 2026-11-02, and the first five actions for the incoming owner."
tags: [tahirs-list, handoff, executive-summary, state-of-the-system]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: docs/handoff/07-current-state-and-risks.md
    title: "Current state and risks (evidence for claims here)"
  - resource: docs/handoff/05-operations-and-infrastructure.md
    title: "Operations and infrastructure"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Kanban task history (115 done, 1 blocked, 4 archived)"
---

# 00 — Executive Handoff

## Why this document exists

The founder is replacing the asynchronous, multi-agent model with a synchronous
one because the async model did not scale for a single human owner. That is a
fair call, and it is the right problem to fix. The failure was not the quality of
the work; it was the **legibility** of the work. Too many agents, too many
channels, too much activity that was invisible unless you went looking for it.

So this handoff optimizes for the opposite: one place, plain language, explicit
uncertainty, and a short list of what to do next.

## What Tahir's List is

**Established.** A listing-first web product that helps Muslims reliably find
halal food. Two things differentiate it from existing directories:

1. **A granular hand-cut filter.** A boolean attribute on listings. The original
   PRD spoke of "hand-cut vs machine-cut"; the team collapsed this to a single
   hand-cut on/off switch (`sc-42`). There is no machine-cut concept in the
   product.
2. **A formal verification process.** Anyone can add a restaurant; it starts
   **unverified**. An owner claims it, consents to AI-assisted review, uploads
   proof of ownership plus a photo of the halal certificate. A hosted vision
   model suggests a verdict; a human **Verification Committee** approves or
   denies. Verified listings show the certificate and a green checkmark.

Supporting pillars: search by location with filters (hand-cut, price, cuisine,
rating, distance), favorites, owner listing management, crowd-sourced edits, and
browser extensions for Google and Yelp (**in MVP scope**, not yet built).

The brand pivot to **Tahir's List** (from "Halal Food Finder") is complete in
documentation and partially in code. Tagline: **"find halal food"**. Mascot:
**Tahir**. Primary lockup: **C2** (Tahir head app-icon + wordmark + tagline).
See [01-product-vision.md](01-product-vision.md).

## The honest state as of 2026-11-02

**Observed.**

- The repository is wherever you cloned `github.com/Losers-Lab/tahirs-list`. 223
  commits between 2026-08-28 and
  2026-09-04. The last code commit was **2026-09-04**; the last Kanban card
  completed **2026-09-09 21:31**.
- The project has been **dormant for roughly eight weeks**. The scheduled
  automation kept firing (the coordination gate ran ~1,340 times, the sprint
  planner ~88 times, the deploy watchdog ~3,925 times) but there was no work
  queued, so nothing moved.
- **The preview stack is partially down.** `hff-backend` is up and its
  `/actuator/health` returns `{"status":"UP"}`. `hff-frontend` is in a restart
  loop (`exit 254`) and is not listening on port 3000. The Cloudflare tunnel is
  up and returns **502** for `https://dev.tahirslist.com` because it cannot reach
  the frontend origin. PostGIS and MinIO are healthy.
- **No production host is provisioned.** Hetzner was *ratified* as the deploy
  target in `ARCHITECTURE.md`, but I found no SSH config, no server, and no
  deployed production artifact. The only running environment is the local Docker
  stack behind the dev tunnel. Treat "production" as **unbuilt**.
- **No open pull requests.** The GitHub queue is empty.
- **One blocked Kanban card:** `t_63bc37f1`, Maryam, "Listing image management
  (frontend) — add/remove image UI on manage screen [sc-53/sc-54]", blocked with
  the reason *"Iteration budget exhausted (40/40)"*. That is a worker-resource
  failure, not a product blocker. The backend endpoints it depends on are merged.

**Inferred.** The stack likely broke when Docker Desktop's WSL bind mount went
stale for the long-lived containers, and the watchdog never repaired it because
its liveness check reads Docker's `.State.Running`, which is `true` during the
brief up-phase of a crash loop. I verified the mount configuration itself is
valid by running the same bind mount in a fresh container — the files were
present. This is a diagnosis to confirm, not a proven root cause.

## What was actually accomplished

**Established** from the Kanban ledger (115 completed cards) and git history.
Grouped, not exhaustive:

- **Foundations:** backend hexagonal skeleton with TDD harness; Next.js frontend
  skeleton; design tokens and trust components; containerized build scripts.
- **Auth and security:** create account, login, JWT verification filter,
  server-side logout, refresh-token rotation in HttpOnly cookies, security
  headers, fail-fast config, actuator lockdown, auth rate limiting, Argon2id.
  Reviewed by Omar, adversarially tested by Yusuf.
- **Listings and search:** create/read listings, PostGIS geo search, filters for
  hand-cut, price, cuisine (AND/OR), rating, distance; pickup/delivery service
  mode; structured address fields and map preview; partial-halal modeling;
  alcohol-served attribute; AI consent on claim.
- **Trust and verification:** verification provider seam with hosted-AI adapter,
  conservative verdict policy, VC approve/deny queue and RBAC, certification
  metadata on the read surface, verification committee review persistence.
- **Images:** MinIO-backed S3 port, multi-width thumbnail variants, responsive
  `srcset` on cards, seed photo ingest, owner add/remove image endpoints.
- **Owner tools:** owner listing edit backend (`PATCH /v1/listings/{id}`) and
  frontend manage screen; listing image add/remove backend.
- **Favorites:** backend endpoints plus frontend provider and saved page.
- **Brand:** Tahir mascot v2, C2 lockup, favicon/app-icon set, header/footer
  application.

Full ledger with dates and owners: [06-work-ledger.md](06-work-ledger.md).

## What we hoped to accomplish next

**Established** from Shortcut. Sprint 6, "Owner Tools: Manage Your Listing"
(2026-09-03 → 2026-09-10), contains six stories: Add Image (#53), Restaurant
Information Management (#23), Approve or Deny Restaurant Edits (#74), Remove
Image (#54), Edit Unverified Restaurant (#47), Edit Verified Restaurant (#48).
Two are in development (#53, #23); four sit in Prioritization. Backlog also holds
~35 stories in Prioritization and a founder-directed cost-hardening epic
(#196) with four open stories.

Details and open product questions: [08-roadmap-and-open-questions.md](08-roadmap-and-open-questions.md).

## The first five things to do

In this order. Do not skip ahead.

0. **On a brand-new computer, start with
   [10-fresh-machine-setup.md](10-fresh-machine-setup.md).** It goes from a machine
   with nothing installed to a running stack: Docker per OS, backing services,
   backend, frontend, smoke tests, teardown, troubleshooting.
1. **Read this bundle from the repository.** It is committed on `main`; clone and
   read it there. Nothing else matters if the knowledge is lost.
2. **Restore the preview.** Recreate `hff-frontend` (and `hff-backend` if needed)
   from the repo, then confirm `https://dev.tahirslist.com` serves and
   `http://localhost:8080/actuator/health` is `UP`. Fix or replace the watchdog's
   liveness check so a crash loop cannot look healthy. See
   [05-operations-and-infrastructure.md](05-operations-and-infrastructure.md).
3. **Reconcile Shortcut to reality.** Two stories say "In Development" but there
   is no active work. Either resume them or move them back. Close or extend
   Sprint 6. The board must reflect truth before you plan on top of it.
4. **Clear the one blocked card.** `t_63bc37f1` is blocked on an iteration budget,
   not a dependency. Re-dispatch it with a larger budget or split it. The backend
   endpoints already exist.
5. **Decide the operating model.** You are moving to synchronous. Pick one place
   for status (Shortcut or the repo), one place for execution (Kanban or direct),
   and one weekly review. Then delete or disable the automation you are not using.
   Half-running automation is worse than none.

## What I would change about how this was run

Said plainly, because it is the most useful thing I can leave behind:

- **Too many agents for one product.** Seven specialist personas produced good,
  well-reviewed work, but the coordination cost landed on one human. A smaller
  team with clearer ownership would have been easier to follow.
- **Automation without a human-facing digest.** The cron gates kept the machine
  honest but produced no readable narrative. A daily five-line summary would have
  solved most of the "I don't know what's going on" problem.
- **The Kanban became the only memory.** Decisions lived in PR comments, task
  bodies, and Discord threads. This bundle exists because that is not durable
  enough for a handoff.
- **The deployment was never promoted out of "preview".** Everything ran on one
  local Docker stack. That made the product feel less real than it was.

## What I am not sure about

Stated so you do not inherit my assumptions as facts:

- Whether the founder intends to keep the specialist personas at all, or collapse
  them into one agent. The brief says "a new agent", singular.
- Whether the eight-week dormancy was intentional (a pause) or drift. I found no
  note recording a decision either way.
- Whether `dev.tahirslist.com` is meant to be a public preview or an internal one.
  It is currently reachable but broken.
- Whether the browser extensions are still in scope for M1. `ARCHITECTURE.md` says
  MVP scope; no extension code exists in the repo.

## Who to ask

- **Product, scope, brand, money:** the founder (Arham).
- **Backend, schema, migrations, PostGIS:** Hamza's work is the reference; his
  rules are in [04-team-and-roles.md](04-team-and-roles.md).
- **Frontend and design intent:** Maryam (frontend) and Fatima (design).
- **Security posture:** Omar's reviews are in `docs/security/` and on PRs. Note
  the persona debt described in [04](04-team-and-roles.md).
- **QA conventions:** Yusuf's work is in `docs/qa/`.
- **Research and external services:** Aisha's briefs are in `docs/research/`.

## A note on tone

The founder asked for honesty and no exaggeration. So: the codebase is in better
shape than the operating model was. The backend is solid, tested, and running.
The frontend is well-structured but currently broken in preview. The product
thinking is thorough. The delivery rhythm was not. Fix the rhythm first; the code
will follow.

---

<!-- ===== 01-product-vision.md ===== -->

---
type: handoff-document
title: "01 — Product Vision: What Tahir's List Is"
description: "The product definition, personas, core model, brand, and the product decisions that were made versus the ones still open."
tags: [tahirs-list, product, vision, personas, brand, prd]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: README.md
    title: "README — product overview and personas"
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE — core product model and open questions"
  - resource: brand/tahir/BRAND-DECISIONS.md
    title: "Brand decision log (founder-approved)"
  - resource: app.shortcut.com/halal-food-finder
    title: "Shortcut — PRD, Use Case Brief Descriptions, Use Case Diagram, prioritization docs"
---

# 01 — Product Vision

## The problem

**Established** (README + PRD summary). Verifying that food is genuinely halal is
hard, especially outside Muslim-majority countries. Two things matter intensely
to the audience and are poorly served by existing directories:

1. **Whether the meat is hand-cut (Zabiha).** Many people will not eat
   machine-cut meat. Existing sites rarely expose this reliably.
2. **Whether a halal claim is actually verified.** A "halal" label on a listing
   can be self-asserted, stale, or wrong. Certification expires and is revoked.

The product's answer is a **listing-first directory** with explicit trust signals:
anyone can add a restaurant, and verification is a formal, auditable process with
a visible outcome.

## The four product pillars

**Established** from the README and PRD:

1. **Discoverable, searchable listings.** Search by location (Google Maps
   autocomplete, current-location opt-in) with filters for hand-cut, price,
   cuisine (AND/OR chaining), rating, and distance.
2. **Trust through verification.** Restaurants list immediately as unverified.
   Owners claim them by submitting proof of ownership plus a photo of their halal
   certification. Verification is assisted by a vision model and reviewed by a
   human Verification Committee in early stages. Target turnaround ~1 week.
3. **Verified certification display.** Verified restaurants show the posted
   certification image and a green checkmark badge.
4. **Browser extensions** for Google and Yelp that surface the platform's listing
   and verification status on those sites, keyed by address matching. **In MVP
   scope per ARCHITECTURE.md; not built as of this handoff.**

## Personas

**Established** (README table; full detail in Shortcut "Use Case Brief
Descriptions"):

| Prefix | Persona | Description |
|--------|---------|-------------|
| U | User | A person wanting to find food to eat. States: not logged in / logged in. |
| RO | Restaurant Owner | A user who owns a restaurant, not yet registered as such. |
| VRO | Verified Restaurant Owner | An owner verified as the owner of a listing. |
| VC | Verification Committee | Reviews verification requests. |
| IC | Issues Committee | Reviews reported issues. |
| ET | Extension Trigger | Trigger listened by the browser extensions. |

**Founder-ratified auth tuning (2026-08-29):** VC and IC are **roles on existing
accounts**, not separate account types. Extension API keys rotate on a schedule.
Refresh-token lifetime ~30 days.

## Core product model

**Established** (ARCHITECTURE.md §1.1–1.2, derived from the PRD):

- Users search by **location** and filter by **hand-cut**, **price**, **cuisine**
  (AND/OR, default **OR**), **rating**, and **distance**.
- Listings carry halal-relevant attributes: an **alcohol-served** flag and, in MVP
  scope, **partial-halal** status (whole-restaurant vs partial).
- A listing starts **unverified**; an owner can claim it; on verification it
  becomes **Verified** and the owner gains control.
- Verification is AI-assisted with mandatory human review. The state machine is
  `SUBMITTED → AI_REVIEW → AI_SUGGESTED → HUMAN_REVIEW → APPROVED/DENIED`
  (+ `REVERSED`).
- Users can view a restaurant (address, phone, hours, photo, Google/Yelp reviews,
  posted certification for verified listings), favorite restaurants, submit
  crowd-sourced edits, add images, create accounts, and report issues.

### Domain model (agreed, technology-independent)

**Established** from the PRD PlantUML as recorded in ARCHITECTURE.md:

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
- **Filter** { criteria } — used by **SearchResult**
- **SearchResult** — filters from RestaurantListings

Committees: **VerificationCommittee** (verifications, edits) and
**IssuesCommittee** (reported issues).

> Note: the implemented backend is close to but not identical to this conceptual
> model. It is hexagonal with separate aggregates; read the migrations and
> `backend/domain` rather than assuming the PlantUML maps one-to-one. See
> [02-architecture.md](02-architecture.md).

## The hand-cut decision

**Established** and worth knowing because it changed twice:

- The PRD described hand-cut vs machine-cut.
- The team implemented a machine-cut concept first, then collapsed it in `sc-42`
  to a single **boolean hand-cut** attribute and dropped machine-cut from the UI
  and filters.
- The README now states plainly: "an extra on/off switch — there is no
  machine-cut concept."

If anyone asks for machine-cut filtering, that is a **product change**, not a bug
fix. It would require a founder decision and a migration.

## Brand

**Established** from `brand/tahir/BRAND-DECISIONS.md` (founder-approved
2026-08-30):

| Decision | Detail |
|----------|--------|
| Product name | **Tahir's List** (tahirslist.com). Pivot from "Halal Food Finder". **Not** "Halal Market". |
| Tagline | **"find halal food"** — lowercase, not "find pure halal food". |
| Mascot | **Tahir**. Canonical image `brand/tahir/tahir-canonical.jpg`; character bible in `CHARACTER.md`. |
| Primary lockup | **C2** — Tahir head app-icon + wordmark (red apostrophe) + tagline. Used everywhere: header, footer, favicon, app icon. |
| C1 hang-off lockup | **Rejected** — "not working"; shelved. |

**Current theme:** light (paper/kraft palette) is the launch default. A dark theme
(C4 treatment: ink `#211B12` ground, paper `#FBF7EE` text, soft red `#E8937D`
apostrophe) is **backlog**, not built. Do not ship dark mode as a "quick win"
without a proper theme pass — the decision log says so explicitly.

**Brand backlog (do not build now):** dark theme, Tahir loading animation (mascot
eating food on slow loads), Tahir expression/pose sheet, "Tahir's Favorites"
sponsor slot, playful animated logo moments.

## What the product is *not*

**Established** from scope documents and the README:

- Not a review platform. Ratings and review counts come from Google/Yelp official
  APIs, cached server-side for 24h. MVP shows rating + count + "Read on
  [Google/Yelp]" link. Full Yelp review text is deferred to a paid tier. **No
  scraping** (ToS/DMCA risk).
- Not an in-house ML company. Verification uses a hosted vision API
  (Gemini 2.5 Flash, paid tier) behind a `VerificationProvider` port. There is no
  model training pipeline. Human review is mandatory.
- Not a marketplace or ordering platform. No ordering, payments, or delivery
  logistics in scope. "Pickup/delivery service mode" is a *listing attribute* and
  filter, not a transaction feature.

## Open product questions

**Unresolved.** These are real decisions, not implementation details. Do not
answer them in code without a founder decision. Full detail in
[08-roadmap-and-open-questions.md](08-roadmap-and-open-questions.md):

1. **Partial-halal semantics** (U-14). In MVP scope per the founder, but the exact
   modeling is still open: how does the verification tag relate to whole-restaurant
   vs partial status? The code has a partial-halal representation (`V18`
   migration, `sc-119`), but the product semantics were never fully ratified.
2. **Alcohol-served attribute** (U-15). In MVP scope. Field and filter exist
   (`V11`, `sc-118`). Remaining question is display/filter semantics and whether
   "serves alcohol" should affect verification status.
3. **Certification expiry and revocation.** Show the expiry date? What happens to a
   verified badge when a certificate lapses? Founder flagged this as open.
4. **Kosher restaurants.** Should they be listed? Unresolved in the PRD.
5. **Beyond restaurants.** Meat shops, grocers, food trucks? Unresolved.
6. **Deferred by the PRD:** menu-item entries and analytics date-range
   customizability. Do not build these.

## How to think about product decisions here

The project's own rule, from AGENTS.md, is worth repeating because it is the
cheapest guardrail you have:

> **Do not invent product requirements.** When a requirement is not known, consult
> Shortcut first. When it is genuinely unresolved, mark it as unresolved rather
> than inventing an answer.

And from the persona rules: distinguish what the documentation explicitly says,
what the team has established technically, what you infer, and what requires a
founder decision. Never present an inference as a requirement. That habit is the
single biggest defense against the confusion that motivated this handoff.

---

<!-- ===== 02-architecture.md ===== -->

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

**Established** from controllers in `backend/adapters/web-api`:

| Controller | Responsibility |
|------------|----------------|
| `SignupController` | Account creation |
| `LoginController` | Login, token issuance |
| `RefreshController` | Refresh-token rotation |
| `LogoutController` | Server-side logout |
| `MeController` | Current-user identity |
| `ListingController` | Create/update listing, owner operations |
| `ListingReadController` | Listing read surface |
| `FavoritesController` | Favorites |
| `VerificationClaimController` | Owner claim + verification submission |
| `VerificationCommitteeController` | VC queue, approve/deny |
| `HealthController` | Health endpoint |

Plus image endpoints on listings (`sc-53`/`sc-54`, merged) and `/actuator/health`
for readiness. The OpenAPI spec is committed under `backend/openapi/`; regenerate
frontend types with `npm run codegen:api`.

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
  security headers, actuator lockdown, fail-fast config).
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

---

<!-- ===== 03-development-process.md ===== -->

---
type: handoff-document
title: "03 — Development Process and Engineering Rules"
description: "The mandated workflow, TDD requirement, commit and PR conventions, review division of labor, and how the Kanban execution loop actually worked."
tags: [tahirs-list, process, tdd, git, pr, code-review, kanban, workflow]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: AGENTS.md
    title: "AGENTS — engineering rules and context"
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE §1.4 — engineering process"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_check.sh
    title: "Coordination gate script (action flag logic)"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Kanban task history and block reasons"
---

# 03 — Development Process and Engineering Rules

This document describes how work was supposed to flow. It is the most important
document to follow if you want the next change to be reviewable and safe. It is
also where most of the async-model friction came from, so read the "what to keep
and what to drop" section at the end.

## Source-of-truth split

**Established** from AGENTS.md and the founder's instructions:

- **Shortcut** (`app.shortcut.com/halal-food-finder`) owns product requirements,
  roadmap, epics, stories, iterations, priorities, product decisions, status, and
  product discussion. The authoritative documents there are the **Product
  Requirements Doc**, **Use Case Brief Descriptions**, **Use Case Diagram**,
  **User Story Prioritization** (priority = backlog order filtered by the
  "Product" skill set), **Use Case Prioritization** (filtered by "Development
  Process"), and the **Development Process** doc.
- **Git** owns source code, tests, migrations, configuration, implementation
  history, and pull requests.
- **Hermes Kanban** owns execution: what was dispatched, to whom, and what
  happened. It is the queue, not the product truth.

Keeping these aligned was my job. The alignment drifted when the queue emptied and
nobody reconciled Shortcut. That is the first thing to fix (see
[00](00-executive-handoff.md)).

## The mandated development order

**Established** from the Shortcut "Development Process" doc. Per feature, in order:

1. **Develop User Story**
2. **Develop Use Case**
3. **Update Use Case Diagram**
4. **TDD for the backend server** (tests first)
5. **Front-end development**

Implications:

- **Backend is test-driven.** Write the failing test first. Do not write backend
  implementation before its failing tests unless explicitly directed otherwise.
- Requirements and use cases are authored in Shortcut **before** implementation of
  a substantial feature. A story that exists only in a Kanban card is not a
  requirement.

This order is not decoration. The verification vertical slice plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`) followed it
explicitly: tasks 1–4 backend TDD, task 5 frontend. If you find yourself writing
frontend first, you have skipped a step and will pay for it in rework.

## Engineering ground rules

**Established** from AGENTS.md. These are founder directives, not suggestions.

### Work in small, reviewable increments

Prefer clear tasks with explicit acceptance criteria tied to Shortcut stories. One
well-defined task beats several speculative ones. This was the standing execution
policy: **WIP = 1**.

### No invented requirements or architecture

Derive from Shortcut or agreed docs. Mark unknowns as unknown. If a requirement is
missing, ask; do not fill the gap with a plausible guess and ship it.

### Verification before claiming done

A change is "done" only when its acceptance criteria are verified — tests pass and
behavior is confirmed. Code that compiles is not done. A demo on localhost is not
done. A PR that "should work" is not done.

### Commit hygiene

- **Incremental commits if and only if the build is passing.** Never commit on a
  red build. Fix or isolate first.
- **Run the formatter before committing** if the project has one configured (run it
  in the containerized toolchain).
- **Prefix every incremental commit with `[<STORY>]`**, e.g. `[SC-131]`. The story
  is the Shortcut story the work belongs to.
- **Message standards:** max 50 characters excluding the prefix, capitalize the
  first word, no trailing period, imperative mood. Example:
  `[SC-131] Add JWT verification filter`.
- Keep commits focused and logically separated.

### PR-based review flow

- **Every** feature or fix goes branch → push → **open a GitHub PR** → merge to
  `main` only after review. Direct pushes or merges to `main` from feature branches
  without a PR are not allowed.
- **CI/build status must be green on the PR head** before merging.
- **No merge without an approving review recorded on the PR.**
- Security-sensitive work (auth, trust, integrations) additionally requires
  **Omar's explicit approval**.

### PR and comment attribution

This is unusual and important. Read it carefully.

**Established.** Commits intentionally attribute to the shared GitHub account
(**Arham4**). Do **not** change that. Keep the PR title convention
(`[SC-###] ...`, no agent prefix). Instead, the acting specialist must be
identified **inside the PR**:

- State the acting agent's profile name in the **PR body** (e.g. an `Author: hamza`
  line or `<!-- attribution: hamza -->` marker).
- Open each **comment or review comment** with `[hamza] ...`.
- If a human wrote it, use the human's name.

This exists because git author and PR titles are shared, so attribution had to live
somewhere auditable. It works, but it is easy to forget. If you drop the
convention, PR authorship becomes unattributable.

### The shared-account approval workaround

**Established.** Because the reviewer and author are the same GitHub account
(Arham4), GitHub blocks a formal `APPROVED` review on your own PR. The team's
workaround, encoded in the coordination gate prompt:

> An open PR **is** merge-approved when an attributed reviewer comment (starting
> `[omar]` or `[yusuf]`) states **"Verdict: APPROVE"** — treat that as a valid
> approving review even if `reviewDecision` is empty or `COMMENTED`.

Still required: the PR must be **MERGEABLE** (no conflict) and the build must be
green. If a PR is conflicting, rebase or merge `main` in; do not block silently.

If you move to a proper multi-account setup, this workaround can be deleted. Until
then, it is the only way to satisfy "no merge without approval" on a single account.

### Security review division of labor

**Established** (founder-ratified):

- **Omar — Security Reviewer (static, code-level).** Reviews diffs and PRs for
  vulnerability classes; approves or blocks PRs on security grounds; defines
  security requirements, threat models, and attack cases. Does **not** run exploit
  scripts or own test tooling.
- **Yusuf — QA / Adversarial Testing (dynamic, runtime).** Verifies acceptance
  criteria against real behavior; owns the "break it" pass (running the app in
  containers, executing Omar's attack cases); owns QA conventions and regression
  checks. Reports findings; does **not** adjudicate security risk — Omar does.
- **Handshake:** Omar defines what to attack and why → Yusuf executes and reports →
  Omar adjudicates (real risk vs. accepted).

This split is genuinely good and worth preserving. It prevents the common failure
where the person who wrote the code also declares it secure.

### Honesty in status reporting (amanah)

Report real results. Surface blockers and unknowns rather than papering over them.
Do not claim success without verifying it. This was stated as a religious and
professional obligation, and it is the rule most worth carrying forward.

## How the execution loop actually worked

**Established** from the cron job definitions and `hff_check.sh`.

There were four scheduled jobs:

| Job | Schedule | Role |
|-----|----------|------|
| HFF coordination gate | Every 30 min, 00:00 and 09:00–23:00 | Merge review, dispatch, unblock |
| HFF sprint plan + execute | Daily 09:00 and 17:00 | Sprint lifecycle, planning, opening/closing |
| HFF coordination gate (overnight) | 03:00 and 06:00 | Same as gate, quiet hours |
| HFF deploy watchdog | Every 15 min | Self-heal the preview stack |

### The action-gated design

The gate is deliberately **deterministic and cheap**. `hff_check.sh` prints a
fingerprint containing:

- `action=0|1` — whether a coordination action is needed
- `prs=<sorted open PR numbers>` — so the fingerprint changes when a PR opens,
  merges, or closes
- `reviews=<per-PR reviewDecision:states:last-review-timestamp>` — so the
  fingerprint changes when a review lands on an **already-open** PR

The Hermes cron `monitor` gate compares the output to the previous tick and wakes
the LLM **only when the fingerprint changes**. This prevents the agent from waking
every 30 minutes to say "nothing to do."

The action logic, in priority order:

1. If any PR is open → `action=1` (merge review).
2. Else if ready cards exist and no worker is running → `action=1` (dispatch).
3. Else if blocked cards exist → `action=1` (unblock).
4. Else `action=0`.

Two bugs were fixed during development and are documented in the script's comments;
both are worth knowing because they illustrate the failure mode:

- **Count-vs-list bug:** emitting only a count meant a *new* PR arriving while
  another was already open did not change the hash, so the gate never woke. Fixed
  by emitting the sorted PR list.
- **Review-on-open-PR bug:** the PR list does not change when a review lands on an
  already-open PR, so the gate never re-evaluated. Fixed by emitting per-PR review
  state and timestamps.

### The agent's job in a tick

The gate prompt told the agent to:

1. Read the injected diff; if it is only cosmetic, treat as nothing to do.
2. Run `hermes kanban list`.
3. **merge_review:** verify and merge open PRs (with the approval workaround above).
4. **dispatch:** dispatch the next ready card (`hermes kanban dispatch`), WIP=1.
5. **unblock:** clear resolved blockers.
6. If nothing was done or nothing new, respond with **exactly** `[SILENT]`.
7. Only report if something actually happened (merged/dispatched/unblocked/synced).

The `[SILENT]` convention is important: it kept the Discord channel quiet so that a
message meant something. If you keep any automation, keep that property.

### The sprint job

The daily 09:00/17:00 job owned the sprint lifecycle end-to-end with full autonomy:

- Diagnose sprint state **from work, not the calendar**. An iteration end date alone
  is not evidence the sprint is alive.
- A sprint is **exhausted** when no card is running and no ready/todo card is
  actually executable (founder-gated cards do not count).
- A sprint is **healthy** when there is running work or executable ready cards.
- If healthy: do nothing, report honestly, stop.
- If exhausted or absent: close the old iteration, create a new one named
  `M1 Sprint N — <Theme>` with a real ~7-day window, and plan a coherent milestone
  across backend, frontend, edge cases, and review gates.
- Source scope **only** from real Shortcut stories in backlog epics that are not
  done. Never invent features. Own-initiated tickets are bug fixes only.
- Reconcile against the repo before queueing: inspect what is already built so you
  do not re-card done work. Verify the foundation is green before dispatching on
  top of it.
- Every story goes in its epic **and** its iteration (founder rule).

### Iteration budget behaviour

**Observed.** Kanban tasks carry a max-iteration budget. When it is exhausted the
card is blocked with a message like *"Iteration budget exhausted (40/40) — task
could not complete within the allowed iterations."* This is a worker-resource
failure, not a product blocker. The blocked card `t_63bc37f1` failed this way.

If you see this, the fix is to increase the budget or split the card — not to
investigate the product.

## What to keep and what to drop

You are moving to a synchronous model. My recommendation, stated as a
recommendation and not a requirement:

**Keep:**

- Shortcut as product truth; the repo as implementation truth. The split is sound.
- TDD for backend. It is the reason the backend is in good shape.
- PR-based review with green build and a recorded approval. This caught real issues.
- The attribution convention while the GitHub account is shared. Cheap and useful.
- The security/QA split between static review and dynamic break-it testing.
- The honesty rule. It is the cheapest quality mechanism you have.

**Drop or simplify:**

- The seven-persona structure, unless the founder explicitly wants to keep it. It
  produced good work but the coordination overhead is what made the project hard to
  follow. A single agent with clear domain checkpoints would be easier to manage.
- The 30-minute action gate. In a synchronous model you will know what is happening;
  a 30-minute autonomous loop is more machinery than you need. Keep the deploy
  watchdog only if you fix its liveness check (see [05](05-operations-and-infrastructure.md)).
- The 42 historical worktrees under `.worktrees/`. They are noise now. Prune them
  after confirming nothing unmerged is needed.
- The `[SILENT]` convention only if you no longer use Discord as the status channel.
  If you do, keep it — a silent channel is a feature.

---

<!-- ===== 04-team-and-roles.md ===== -->

---
type: handoff-document
title: "04 — Team and Roles"
description: "The specialist personas that worked on Tahir's List, what each actually owned and delivered, and the known persona debt you inherit."
tags: [tahirs-list, team, agents, roles, personas, handoff]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: ~/.hermes/profiles/*/SOUL.md
    title: "Agent persona definitions"
  - resource: AGENTS.md
    title: "AGENTS — contact/coordination section"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Task assignee history"
---

# 04 — Team and Roles

## The roster

**Established** from the profile directories and AGENTS.md. Eight profiles exist;
seven worked on Tahir's List. The eighth (`olive`) is unrelated.

| Profile | Role | Domain |
|---------|------|--------|
| `adnan` | Product & Engineering Manager | Product vision, sequencing, dependencies, review gates, founder escalation |
| `fatima` | Product Designer | Flows, layouts, IA, accessibility, trust/verification messaging, design handoff |
| `hamza` | Backend & Systems Engineer | APIs, schema, PostGIS, business logic, TDD, migrations |
| `maryam` | Frontend Engineer | Next.js implementation, API integration, UI states, tests |
| `aisha` | Research & Data Specialist | External APIs, datasets, provenance, licensing, seed data research |
| `yusuf` | QA & Testing Engineer | Acceptance verification, edge cases, regressions, break-it pass |
| `omar` | Security Reviewer | Static code review, threat models, attack cases, security sign-off |
| `olive` | Business intelligence / marketing ops | **Unrelated.** Cloud Modest Apparel. Do not confuse with Tahir's List. |

## What each actually delivered

**Established** from the Kanban ledger (115 completed cards) and git history. This
is the honest version, including duplication and oddities.

### Adnan — Product & Engineering Manager

Owned sequencing and coordination. Delivered the MVP execution plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`), kept the
Kanban moving, ran merge review, and made the call to defer the stamps design
review to the next sprint. Also the author of this handoff.

**Debt note:** I did not produce a human-readable daily digest. That is the single
biggest process failure that led to this transition. See [00](00-executive-handoff.md).

### Fatima — Product Designer

Delivered the design foundation (tokens + trust components + auth screen specs), the
Tahir mascot and logo identity exploration, the mascot v2 art swap across screens,
and the production favicon/app-icon set. Also produced design docs for detail page,
search/browse, trust components, auth screens, mobile bottom tab, verification seam,
partial-halal, AI consent, owner claim, image variants, and the Tahir brand.

**Where her work lives:** `docs/design/`, `brand/tahir/`, `sketches/`.

**Note:** Fatima's design docs are *intent*. Not all of them are implemented. Check
the ledger and code before assuming a design is live.

### Hamza — Backend & Systems Engineer

The heaviest contributor. Delivered the backend skeleton and TDD harness, account
creation, login, JWT verification filter, refresh-token cookie lifecycle, fail-fast
config and security headers, listing create/read, PostGIS search, filters (hand-cut,
price, cuisine, rating, distance), favorites endpoints, verification provider seam
and hosted-AI adapter, conservative verdict policy, VC review persistence and RBAC,
certification metadata exposure, MinIO image variants behind an `ImagePort`, seed
photo ingest, owner listing update, owner image add/remove endpoints, partial-halal
modeling, alcohol-served attribute, pickup/delivery service mode, structured address,
and Photon geocoder adapter.

He also did substantial conflict resolution and rebasing: PR #46 favorites conflict,
PR #61 structured-address conflict, PR #54 review blockers.

**Where his work lives:** `backend/`, especially `domain`, `application`,
`adapters/*`, and the migrations.

### Maryam — Frontend Engineer

Delivered the frontend skeleton and design-token base, signup/login UI, cookie-based
session lifecycle, security headers via `next.config`, the VC review UI, hand-cut
boolean UI, the QA fix for the duplicate Unverified badge and desktop bottom nav,
responsive multi-width `srcset` for card thumbs, pickup/delivery display and filter
UI, structured address rendering and map preview with pin, the owner listing
manage/edit screen, and the responsive restaurant images work.

**Blocked:** "Listing image management (frontend) — add/remove image UI on manage
screen [sc-53/sc-54]" (`t_63bc37f1`). This is the one unfinished card.

**Where her work lives:** `frontend/src/`.

### Aisha — Research & Data Specialist

Delivered the seed restaurant research for NYC, Dallas, Austin, and Toronto, and the
`sc-157` seed hero photos manifest. Her briefs are in `docs/research/`.

**Method she followed:** Fact → Evidence → Interpretation → Recommendation. She did
not present assumptions as facts. That discipline is worth keeping.

### Yusuf — QA & Testing Engineer

Delivered acceptance-criteria reviews at story start, break-it passes on the refresh
cookie flow and auth surfaces, and the QA assessment and testing conventions in
`docs/qa/`. He reported defects rather than fixing them, per his scope discipline.

**Where his work lives:** `docs/qa/` and PR review comments prefixed `[yusuf]`.

### Omar — Security Reviewer

Delivered security reviews on the JWT verification filter, the refresh-cookie flow,
auth rate limiting, and multiple PR approvals across auth and verification work.
His review is in `docs/security/auth-security-review-2026-08-29.md` and on PRs
prefixed `[omar]`.

**⚠ Known persona debt:** `omar`'s `SOUL.md` is the **generic Hermes Agent prompt**,
not a security-reviewer persona. It contains only a PR-review efficiency note. The
security-reviewer role was enforced by AGENTS.md and by task assignment prompts, not
by his persona file. If you keep Omar, fix his `SOUL.md` first. If you collapse to one
agent, carry the security rules forward explicitly — do not rely on a persona file
that does not contain them.

### Olive — unrelated

`olive` is the business-intelligence and marketing-operations agent for **Cloud Modest
Apparel**, a Shopify store. Her `SOUL.md` covers competitor intelligence, email
strategy, pop-up/event hunting, and partnership commissions. She has nothing to do
with Tahir's List. Do not assign her HFF work, and do not assume her presence means
the HFF team is larger than it is.

## How the personas were expected to behave

**Established** from their `SOUL.md` files. Summarized because the working style is
part of the handoff — the next owner may keep some of these.

- **Adnan (PM):** calm, organized, quietly demanding. Turns mess into five bullets.
  Comfortable saying "we don't know that yet", "that's an assumption, not a
  requirement", "let's ask Hamza", "Fatima should look at this before we build it",
  "we're solving the wrong problem". Dislikes unnecessary meetings, abstractions, and
  work. Keeps "a little barakah" — do things properly, be honest about uncertainty,
  do not sacrifice quality to appear productive. Distinguishes documentation, technical
  fact, inference, and founder decision. Never presents an inference as a requirement.
- **Fatima (Design):** thoughtful, creative, detail-oriented, slightly opinionated
  about ugly interfaces. Believes most interfaces have too many buttons. Asks "what
  does the user think is happening here?" Does not accept "that's how the API works"
  as a UX justification. Treats a halal verification badge as a trust artifact, not
  decoration. "A little ihsan" — make it beautiful, but useful first.
- **Hamza (Backend):** methodical, skeptical, pragmatic. Favorite question: "okay, but
  what happens when it fails?" Prefers boring, reliable infrastructure. Cares about
  data integrity — "a database isn't a bucket where the frontend throws JSON."
  Occasionally responds to ambition with "we could do that. I'm not sure we should."
  "Measure twice. Migrate once."
- **Maryam (Frontend):** practical, detail-oriented, allergic to unnecessary
  abstractions. Skeptical of premature abstraction, giant component files, clever
  state management, unnecessary dependencies, and CSS requiring spiritual intervention.
  Cares about accessibility, responsive behavior, performance, polish. "Ship clean
  code. Leave tomorrow's Maryam something she won't hate."
- **Aisha (Research):** separates fact, evidence, interpretation, recommendation.
  Prefers primary sources. Says when evidence is insufficient. Does not invent missing
  information to unblock someone. Finishes the assigned question and stops.
- **Yusuf (QA):** assumes the happy path is already tested. Prioritizes malformed input,
  empty/missing data, permission failures, network failures, repeated actions, stale
  state, boundaries, regressions. Distinguishes expected behavior / defect / uncertainty.
  Reports what was actually verified. Does not implement fixes unless assigned.
- **Omar (Security):** static, code-level. Reviews diffs, not whole files (he has a
  note telling him to use `gh pr diff`/`git diff`). Defines attack cases; does not run
  exploits. Adjudicates risk.

## Coordination rules that mattered

**Established** from AGENTS.md:

- Adnan coordinated requirements, engineering, QA, security review, and founder
  escalation. Specialists owned their domains and were expected not to need
  micromanagement.
- When a decision materially affected architecture or the product, the specialist was
  consulted and the founder involved. Consequential assumptions were not to be made
  silently.
- "Know when another specialist's expertise is needed. Don't involve people
  unnecessarily, but don't make domain decisions yourself when the team has a
  specialist who should answer them."

## My honest assessment of the team model

Stated as an assessment, not a requirement:

The personas were well-written and produced genuinely good, well-reviewed work. The
backend is the strongest evidence: 68 test files, TDD throughout, real security review,
real adversarial QA. The design thinking is also strong and well documented.

The failure was structural, not individual. Seven specialists plus one manager, all
communicating asynchronously, meant that understanding the state of the project
required reading Kanban cards, PR comments, design docs, research briefs, and Discord
threads. No single artifact told the founder what was happening. That is why this
handoff exists and why a synchronous model is the right next step.

If you keep specialists, keep **three**: backend, frontend, and one reviewer/QA
function. Design can be a checkpoint rather than a standing persona. Research can be
a task rather than an agent. That would cut the coordination surface roughly in half
without losing the quality controls that actually worked.

---

<!-- ===== 05-operations-and-infrastructure.md ===== -->

---
type: handoff-document
title: "05 — Operations and Infrastructure"
description: "How the Tahir's List stack actually runs: Docker services, build scripts, deployment paths, cron automation, the current outage, and the production plan that was never executed."
tags: [tahirs-list, operations, infrastructure, docker, deployment, cloudflare, cron, watchdog]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: scripts/backend-test.sh
    title: "Containerized backend build/test"
  - resource: scripts/frontend-test.sh
    title: "Containerized frontend build/test"
  - resource: scripts/backend-live.sh
    title: "Live profile backend launcher"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_watchdog.sh
    title: "Deploy watchdog backstop"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_redeploy.sh
    title: "GitHub webhook redeploy trigger"
  - resource: ~/.hermes/profiles/adnan/cron/jobs.json
    title: "Scheduled jobs"
  - resource: backend/docker-compose.yml
    title: "Backing services definition"
---

# 05 — Operations and Infrastructure

> **On a new machine?** This document describes the host the project was built on,
> including automation that lives outside the repository. For a clean, machine-independent
> setup procedure, read [10 — Fresh Machine Setup](10-fresh-machine-setup.md) first. Come
> back here for the architecture of the running stack, the deploy paths, and the diagnosis
> of the original outage.
>
> **Name changes (2026-11-03):** the project was renamed from Halal Food Finder to
> Tahir's List. Container, network and cache-volume names in the observations below are
> the ones seen on the old host: `hff-postgis`/`hff-minio`/`hff-backend`/`hff-frontend` are
> now `tahirs-list-*`, the compose project/network `halal-food-finder_default` is now
> `tahirs-list_default`, data volumes are prefixed `tahirs-list_`, and the Gradle/npm cache
> volumes are `tahirs-list-gradle-cache` / `tahirs-list-npm-cache` /
> `tahirs-list-gradle-test-cache`. The Shortcut workspace slug and the Hermes Kanban path
> keep their original names — they are external identifiers, not product branding.

## The short version

Everything runs as **local Docker on the development machine**, exposed through a **stable
Cloudflare named tunnel** at `https://dev.tahirslist.com`. There is **no CI
pipeline** and **no provisioned production server**. The deploy path is a GitHub
push webhook that fires a shell script; a 15-minute watchdog is the backstop.

**As of 2026-11-02 the preview is partially down:** backend healthy, frontend in a
restart loop, tunnel returning 502. Fix procedure is at the end of this document.

## Repository and working state

**Observed.**

| Item | Value |
|------|-------|
| Path | wherever you cloned the repo (see [10](10-fresh-machine-setup.md)) |
| Remote | `https://github.com/Losers-Lab/tahirs-list.git` |
| Current branch | `wt/sc-187-detail-address-map` (**not** `main`) |
| Working tree | **Dirty**: modified `frontend/package-lock.json`, plus untracked logs, brand PNGs, `.gitignore`, `backend-repomix.xml`, `gphoto_method.md`, and `backend/.gradle.stale.*` |
| Last commit | 2026-09-04 `[SC-53] Add owner listing image endpoints (#65)` |
| Commits | 223 total, 2026-08-28 → 2026-09-04 |
| Open PRs | none |
| Historical worktrees | 42 directories under `.worktrees/` |

**First operational action:** get off the feature branch. Stash or discard the local
changes, check out `main`, and confirm the tree is clean before running anything.
The dirty tree is why the watchdog's `git checkout main` could not do its job.

## Docker services

**Observed** at time of writing. Network: `halal-food-finder_default` (bridge).

| Container | Image | Ports | State |
|-----------|-------|-------|-------|
| `hff-postgis` | `postgis/postgis:17-3.4` | 5432 | **Up 21h (healthy)** |
| `hff-minio` | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | 9000 (S3), 9001 (console) | **Up 21h (healthy)** |
| `hff-backend` | `eclipse-temurin:21-jdk` | 8080 | **Up; `/actuator/health` = UP** |
| `hff-frontend` | `node:22` | 3000 | **Restarting loop, exit 254** |
| `hff-tunnel-dev` | `cloudflare/cloudflared` | — | Up 21h |
| `hff-webhook-tunnel` | `cloudflare/cloudflared` | — | Up 21h |

> **2026-11-04 update (Ktor migration):** the app containers are now named
> `tahirs-list-backend` / `tahirs-list-frontend` and the backend has no actuator:
> the health probe is `GET http://localhost:8080/v1/health`. The `hff-*` rows
> above describe the pre-migration host and are kept for history.

### Backing services (`backend/docker-compose.yml`)

Project name `halal-food-finder`. Three services:

- **`postgis`** — Postgres 17 + PostGIS 3.4. DB `halal`, user `halal`, password
  `halal` (dev only). Volume `pgdata`. Healthcheck `pg_isready`.
- **`minio`** — S3-compatible object store. Root user/password `minioadmin`
  (dev only). Volume `miniodata`. Healthcheck on `/minio/health/live`.
- **`minio-init`** — one-shot `mc` container that waits for MinIO and creates the
  private bucket `halal`.

**Data lives in Docker named volumes `halal-food-finder_pgdata` and
`halal-food-finder_miniodata`.** I found **no backup job, no snapshot script, and no
documented restore procedure**. That is the single largest data risk in the system.
If the volumes are pruned or the machine is rebuilt, the seeded listings and any
uploaded certificates are gone. Address this before anything else production-shaped.

### Backend launch (`scripts/backend-live.sh`)

Launches the Ktor backend and the two infra switches are **environment
variables**, not a Spring profile:

- `APP_S3_ENDPOINT` (plus `APP_S3_*` credentials/bucket) → the real
  MinIO-backed `ImagePort` instead of the in-memory stub.
- `APP_SEED_INGEST_ENABLED=true` → the seed-photo ingest runner runs on boot.

**Without `APP_S3_ENDPOINT`** the app boots with `InMemoryImagePort` and seed
ingest off — which is why `GET /v1/listings/{id}/image` 404s in that
configuration. If images are missing, check the env vars first.

`APP_PROFILE` only selects strict production behaviour for the JWT key
(`isProduction`); `live` is descriptive and does not turn features on.

The script does `docker compose up -d` for backing services, then `docker rm -f
tahirs-list-backend` and `docker run` with:

- `-v "$repo_root/backend":/workspace`
- `-v tahirs-list-gradle-cache:/root/.gradle`
- `-w /workspace`
- `--network tahirs-list_default`
- datasource `jdbc:postgresql://postgis:5432/halal`, user/pass `halal`
- `APP_S3_*` env vars defaulting to `http://minio:9000`, `minioadmin/minioadmin`,
  region `us-east-1`, bucket `halal`
- `APP_SEED_INGEST_ENABLED` defaulting to `true`
- command `./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache :bootstrap:run`

It runs the app from source via Gradle, not from a prebuilt image. That is fine
for preview, but it means startup is slow and depends on the Gradle cache
volume.

### Frontend launch

`./scripts/frontend-dev.sh` serves the SvelteKit dev app on `:5173` inside a
`node:24-slim` container (see AGENTS.md and the script header for the mounts and
cache volume). The SvelteKit server proxies `/v1` API calls to the backend via
`API_PROXY_TARGET`, so the backend must be running too.

### Build and test scripts (the only supported paths)

**Established** from AGENTS.md and the scripts themselves. The host has no language
toolchains and must not get any.

| Command | What it does |
|---------|--------------|
| `./scripts/backend-test.sh` | Full `build` (compile + every module's tests + packaging) in `eclipse-temurin:21-jdk`, capped at 3 GB / 2 CPUs. Mounts repo to `/app`, `tahirs-list-gradle-test-cache` to `/gradle-home`, and `/var/run/docker.sock` for Testcontainers. |
| `./scripts/backend-test.sh test-only` | Same container, runs all `test` tasks without packaging. |
| `./scripts/frontend-test.sh` | Frozen `pnpm install` + `svelte-check` + `vitest run` + `vite build` in `node:24-slim`. |
| `./scripts/frontend-test.sh dev-install` | Allows lockfile drift instead of the frozen install. |
| `./scripts/frontend-test.sh test-only` | Skips install; runs check + tests only. |
| `./scripts/frontend-dev.sh` | SvelteKit dev server on `:5173`, proxying `/v1` via `API_PROXY_TARGET`. |

Both scripts find the nearest `backend/` or `frontend/` directory relative to their
own location, so they work from the repo root **and** from a worktree under
`.worktrees/`. That is deliberate and worth preserving.

**Testcontainers settings are mandatory** and baked into `backend-test.sh`:
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`,
`--add-host host.docker.internal:host-gateway`, and
`TESTCONTAINERS_RYUK_DISABLED=true`. Remove them and sibling-container tests fail
with connection-refused errors.

## Deployment paths

There are **two** paths, by design.

### Primary: GitHub push webhook

**Established** from `hff_redeploy.sh` and `hff_redeploy_worker.sh`.

1. A push lands on `refs/heads/main` (including PR merges).
2. GitHub posts to the Hermes webhook platform on port 8644
   (`hff_restart_gateway.sh` exists to restart that gateway).
3. `hff_redeploy.sh` reads the payload. If the ref is not `refs/heads/main`, it exits
   silently. Otherwise it extracts the commit SHA and a clean commit subject.
4. It fires `hff_redeploy_worker.sh` asynchronously with `nohup ... &` so the webhook
   returns in under a second (GitHub retries on timeout).
5. It prints a short confirmation referencing the stable preview URL
   `https://dev.tahirslist.com`.

### Backstop: 15-minute watchdog

**Established** from `hff_watchdog.sh`. Polls every 15 minutes. It redeploys when:

- `git fetch origin main` shows `origin/main` moved past local `HEAD`, **or**
- either `hff-backend` or `hff-frontend` is not running.

It stays silent when healthy (empty stdout = no message, because the cron job is
`no_agent: true`).

**It then:** `git checkout main`, `git pull origin main`, run `backend-live.sh`, and
`docker rm -f hff-frontend` followed by the `docker run` above. It polls
`localhost:8080/actuator/health` for up to 24 × 5s.

### ⚠ The watchdog bug you inherit

**Observed and inferred.** Docker reports `.State.Running = true` during the brief
up-phase of a crash-looping container. The watchdog's liveness check is:

```bash
[ "$(docker inspect -f '{{.State.Running}}' $c)" = "true" ] || NEED_DEPLOY=1
```

So a container that is restarting every 60 seconds can read as "alive" at the moment
of the check. If `main` has not moved, `NEED_DEPLOY` stays `0`, and the watchdog
stays silent while the stack is broken. This is the most likely reason the preview
sat down without an alert. The fix is to check health endpoints or restart counts,
not `.State.Running`.

A second, related fragility: the watchdog's `git checkout main` will fail silently on
a dirty working tree (`2>/dev/null`), and the script has no `set -e`, so it proceeds
to redeploy whatever is checked out. That is how a feature branch can end up running
the "preview".

### Tunnel

**Observed.** Two `cloudflared` containers run continuously:

- `hff-tunnel-dev` serves the stable named tunnel for the preview.
- `hff-webhook-tunnel` serves the GitHub webhook endpoint.

The tunnel config lives in the container token, not in a mounted `config.yml`
(`~/.cloudflared/` is empty on the original host). The token is in `.cloudflared-token.txt` at
the repo root — **gitignored and not tracked** (verified), but still a credential
sitting in the working tree. Move it to a proper secret store when you can.

Tunnel logs at the time of writing show the failure clearly:

```
ERR Unable to reach the origin service ... dial tcp [::1]:3000: connect: connection refused
dest=https://dev.tahirslist.com/ ingressRule=1 originService=http://localhost:3000
```

The tunnel is fine. The frontend origin is not.

## Scheduled automation

**Established** from `~/.hermes/profiles/adnan/cron/jobs.json`. Four jobs
were running. Three delivered to Discord channel `1543111845106286653`; the watchdog
delivered to `local`.

| ID | Name | Schedule | Runs | Monitor |
|----|------|----------|------|---------|
| `ee7b25152cb2` | HFF coordination gate (daytime) | `0,30 0,9-23 * * *` | ~1,340 | `hff_check.sh` |
| `f37d47352a20` | HFF coordination gate (overnight) | `0 3,6 * * *` | ~92 | `hff_check.sh` |
| `827f0630945f` | HFF sprint plan + execute | `0 9,17 * * *` | ~88 | `hff_sprint_gate.sh` |
| `d91459805654` | HFF deploy watchdog | `*/15 * * * *` | ~3,925 | none (script only) |

All four report `last_status: ok`, which is misleading: "ok" means the job executed,
not that the system is healthy. The watchdog has run ~3,925 times and the stack is
still down. See [03-development-process.md](03-development-process.md) for what the
gates do and how their fingerprints work.

**Recommendation:** in a synchronous model, keep at most the watchdog (fixed) and one
daily digest. Disable the rest. The 30-minute gate is solving a coordination problem
you will no longer have.

## Current outage and fix procedure

**Observed** on 2026-11-02.

### Symptoms

- `https://dev.tahirslist.com` → **502**.
- `hff-frontend` → restarting, `exit 254`, port 3000 not listening (`curl` returns
  `000`).
- `hff-backend` → up; `http://localhost:8080/actuator/health` returns
  `{"status":"UP"}`; `/` returns **401** (expected — security is on).
- Tunnel logs: `dial tcp [::1]:3000: connect: connection refused`.
- PostGIS and MinIO healthy.
- Backend logs show repeated `FETCH_FAILED ... HTTP 403` for several seed images
  from `lh3.googleusercontent.com` (Caspian Grill, Halal Bros, Halal Wings). Those
  seed photo URLs are dead or now require auth. This is a **content debt**, separate
  from the outage, and it means some listings will render without photos.

### Diagnosis

**Inferred, high confidence.** The long-lived containers lost their bind mounts (a
known Docker Desktop/WSL2 failure after restarts), so `/workspace/gradlew` and
`/app/package.json` were not visible inside them and the entrypoints failed. I
verified the mount configuration is correct by running the same bind mount in a fresh
container — the files were present. Recreating the containers should restore service.

**Why the watchdog did not fix it:** see the bug above. The container reads as
running during its restart cycle.

### Fix procedure

Run these in order. Do not skip the verification steps.

```bash
cd /path/to/tahirs-list

# 0. Get onto a clean main. The dirty feature branch blocks the deploy scripts.
git stash push -u -m "handoff-preserve-$(date -u +%Y%m%dT%H%M%SZ)"   # or discard
git checkout main
git pull origin main

# 1. Confirm backing services.
docker compose -f backend/docker-compose.yml up -d
docker ps --filter name=hff-postgis --filter name=hff-minio

# 2. Recreate the backend from source with the live profile.
bash scripts/backend-live.sh

# 3. Wait for backend health, then recreate the frontend.
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health)
  [ "$code" = "200" ] && break; sleep 5
done
echo "backend health: $code"

docker rm -f hff-frontend
docker run -d --name hff-frontend --restart unless-stopped -p 3000:3000 \
  --network halal-food-finder_default \
  -v "$PWD/frontend":/app -w /app -v /app/node_modules \
  -v halal-npm-cache:/npm-cache -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://hff-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"

# 4. Verify end to end.
curl -s -o /dev/null -w 'frontend %{http_code}\n' http://localhost:3000/
curl -s -o /dev/null -w 'preview  %{http_code}\n' https://dev.tahirslist.com
docker logs hff-frontend --tail 40
```

If the frontend still exits 254, read its logs before rebuilding. Exit 254 usually
means the container's command failed to start — a missing `package.json`, a broken
`npm` invocation, or a build error. The log will say which.

### After it is up

1. **Fix the watchdog liveness check** so a crash loop cannot look healthy. Check
   `http://localhost:3000/` and `/actuator/health`, or use restart counts, not
   `.State.Running`.
2. **Make the watchdog fail loudly.** A silent watchdog that cannot detect failure is
   worse than no watchdog. Send a message when a redeploy is attempted and when it
   fails.
3. **Get the tree clean** and keep it clean. The deploy path assumes `main`.

## Production hosting: planned, not built

**Established** from `ARCHITECTURE.md` §1.0 (U-08) and the external-services brief.

- **Decision:** Hetzner VPS, **CX/CAX line** (not CPX — CPX prices rose in 2026),
  roughly **CX23 2 vCPU / 4 GB / 40 GB at ~US$6–7/month**, self-hosting Postgres 16/17
  + PostGIS, MinIO, the Ktor app image, and the async verification worker on
  one always-on box.
- **Why self-host:** full extension control so a managed tier cannot drop PostGIS.
- **Alternative considered:** Neon (managed Postgres with native PostGIS, free /
  scale-to-zero) as the zero-ops $0 option.
- **Migration is cheap by design:** Jib OCI images plus env-var connection strings;
  moving the DB is `pg_dump`/restore plus one JDBC URL (<1 day).
- **Avoid provider-specific lock-in.** EKS was rejected as an MVP option
  (~$60–300/month).

**Observed:** no SSH config, no server, no deployed production artifact. The only
running environment is the local Docker stack behind the dev tunnel. **Production is
unbuilt.** Do not describe the product as deployed or live.

**Estimated M1 run-rate:** ≈ $8–10/month total (Hetzner ~$7, domain ~$1, maps ~$0,
Gemini <$1, email $0, storage $0, plus a one-time ~$5 Chrome Web Store fee for
extensions).

## CI: there is none

**Observed.** There is no `.github/workflows/` directory. The rule "CI/build status
must be green on the PR head" was enforced by humans and agents running
`./scripts/backend-test.sh` and `./scripts/frontend-test.sh` manually and reporting
the result. That worked, but it is unverifiable after the fact.

**Recommendation:** add a minimal GitHub Actions workflow that runs both scripts on
`pull_request` and on `push` to `main`. It is a few lines and it removes the single
biggest "trust me" from the process. If you do this first, you will prevent more
breakage than any other infrastructure change.

## Ports and URLs cheat sheet

| What | Where |
|------|-------|
| Backend API | `http://localhost:8080` |
| Backend health | `http://localhost:8080/actuator/health` |
| Backend API root | `/v1/...` (OpenAPI under `backend/openapi/`) |
| Frontend | `http://localhost:3000` |
| Public preview | `https://dev.tahirslist.com` |
| Postgres/PostGIS | `localhost:5432`, db `halal`, user `halal` |
| MinIO S3 API | `http://localhost:9000`, bucket `halal` |
| MinIO console | `http://localhost:9001`, `minioadmin`/`minioadmin` |
| Hermes webhook platform | `http://localhost:8644` |
| GitHub repo | `github.com/Losers-Lab/tahirs-list` |
| Shortcut workspace | `app.shortcut.com/halal-food-finder` |

## Known operational debt (prioritized)

1. **No database backup or restore procedure.** Highest risk. Fix first.
2. **Watchdog cannot detect a crash loop.** Fix the liveness check and make failures
   loud.
3. **No CI pipeline.** Manual green-build reporting is unverifiable.
4. **Dirty working tree on a feature branch.** Breaks the deploy path's assumptions.
5. **42 stale worktrees.** Noise and disk. Prune after checking for unmerged work.
6. **Seed image 403s.** Several seed photo URLs are dead; those listings render
   without images. Re-host the images in MinIO or replace the URLs.
7. **Secrets in the working tree.** `.cloudflared-token.txt` is gitignored but still a
   credential on disk. Move to a secret store.
8. **Dev credentials are defaults.** `halal/halal` and `minioadmin/minioadmin` are
   fine for localhost, unacceptable on a public host. Change them when you provision.

---

<!-- ===== 06-work-ledger.md ===== -->

---
type: handoff-document
title: "06 — Work Ledger: What Was Actually Built"
description: "The factual record of completed work: Kanban statistics, timeline by date and owner, what shipped, and what remains unfinished."
tags: [tahirs-list, ledger, kanban, history, delivery, milestones]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Kanban tasks (115 done, 1 blocked, 4 archived)"
  - resource: git log (223 commits, 2026-08-28 → 2026-09-04)
    title: "Commit history"
  - resource: app.shortcut.com/halal-food-finder
    title: "Epics, stories, iterations, workflow states"
---

# 06 — Work Ledger

This is the factual record, not the narrative. If you need to know whether something
was built, check here and then check the code. If the two disagree, the code wins.

## Numbers

**Established.**

| Metric | Value |
|--------|-------|
| Commits | 223 |
| Commit window | 2026-08-28 → 2026-09-04 |
| Kanban tasks total | 120 |
| — done | 115 |
| — archived | 4 |
| — blocked | 1 |
| — ready / running / todo | 0 |
| Backend test files | 68 |
| Frontend test files | 22 |
| Flyway migrations | 20 (V1–V20) |
| Frontend routes | 9 |
| Backend controllers | 11 |
| Docs: design | 14 files |
| Docs: qa | 2 files |
| Docs: research | 3 files |
| Docs: reviews | 1 file |
| Docs: security | 1 file |
| Shortcut epics | 7 |
| Shortcut workflow states | 6 |
| Stories completed (Shortcut) | 38 |
| Stories in Prioritization | 35 |
| Stories in Development | 2 |
| Stories in Ready for Development | 0 |

**Note the discrepancy:** Shortcut shows 38 completed stories while Kanban shows 115
done cards. Kanban cards include review tasks, QA tasks, fixes, and sub-work that do
not map one-to-one to stories. Do not treat the Kanban count as a story count.

## Commit activity by date

**Established** from `git log --date=short`.

| Date | Commits |
|------|---------|
| 2026-08-28 | 8 |
| 2026-08-29 | 41 |
| 2026-08-30 | 78 |
| 2026-08-31 | 50 |
| 2026-09-01 | 28 |
| 2026-09-02 | 12 |
| 2026-09-03 | 5 |
| 2026-09-04 | 1 |

The velocity curve is steep up and steep down. Nearly 80% of commits landed in the
first four days. This was a concentrated build sprint followed by a taper into
review-and-fix work. After 2026-09-04 there were no code commits at all; the last
Kanban card completed on 2026-09-09.

## What shipped, by domain

**Established** from the Kanban ledger and git history. Grouped for readability; the
full card list is in the Kanban database if you need per-task detail.

### Foundations and scaffolding

- `sc-116` — Backend skeleton + TDD harness (Hamza); frontend skeleton + design-token
  base (Maryam); M1 design foundation: tokens, trust components, auth screen specs
  (Fatima).
- Containerized build scripts for backend and frontend; Docker Compose for PostGIS +
  MinIO.

### Authentication and security

- `sc-39` — Create Account backend, TDD (Hamza).
- `sc-40` — Log In backend, TDD (Hamza); Sign Up + Log In UI (Maryam).
- `sc-131` — JWT verification filter, deny-by-default resource server (Hamza);
  security review (Omar).
- `sc-132` — Server-side logout, TDD (Hamza); approving review (Omar).
- `sc-133` — Refresh-token HttpOnly cookie + session lifecycle (Hamza backend,
  Maryam frontend); attack cases prepared by Omar; break-it pass by Yusuf.
- `sc-134` — Fail-fast config, key handling, error handling, security headers
  (Hamza); frontend security headers via `next.config` (Maryam); actuator lockdown;
  production key guard.
- Auth rate limiting (Bucket4j), Argon2id password hashing, JWT RSA key pair loader.
- `sc-135` — Auth test-gap closure and QA conventions (Yusuf); frontend test gaps.

### Listings and search

- Listing create/read (`POST /v1/restaurants` equivalent), unverified-by-default.
- `sc-43/44` — Price and cuisine filters, including AND/OR chaining.
- `sc-45/72` — Rating and distance filters with straight-line miles.
- `sc-42` — Cutting filter refactor: boolean hand-cut, machine-cut dropped (backend
  Hamza, frontend Maryam).
- `sc-184` — Pickup/delivery service mode on listings plus search filter (backend
  Hamza, frontend Maryam).
- `sc-187` — Structured address fields (city/province/postal/country) plus full
  address rendering and map preview with pin (backend, frontend, and QA fix).
- `sc-118` — Alcohol-served attribute on listings.
- `sc-119` — Partial-halal modeling and cross-contamination index gate; hand-cut
  search. Included a review-blocker fix restoring the trust doc and reconciling the
  `sc-43` test.
- `sc-185` — QA fix: duplicate Unverified badge and bottom nav on desktop.

### Trust and verification

- `sc-117` — Verification provider seam with hosted-AI adapter and human review
  (Hamza); code review (Omar).
- Conservative verdict policy (`ConservativeVerdictPolicyTest`).
- `sc-73` — Verification Committee decision use case, persisted VC reviews and listing
  promotion, VC review endpoints and RBAC, VC review API client and queue state, VC
  approve/deny screen. Certification metadata exposed on the detail read surface.
- `sc-120` — AI consent required on the claim endpoint, consent timestamp persisted on
  reviews; design doc `docs/design/sc-120-ai-consent.md`.
- `sc-46` — Owner claim design and flow.

### Images and media

- `sc-157` — MinIO-backed image variants behind an `ImagePort` (Hamza, TDD); responsive
  restaurant images via `next/image` (Maryam); seed hero photos manifest (Aisha); seed
  photo ingest runner; live profile wiring.
- `sc-183` — Multi-width thumbnail variants for responsive `srcset` (backend Hamza,
  frontend Maryam).
- `sc-180` — Next/image optimizer configured to allow `/v1` image URLs.
- `sc-53` — Owner listing image add/remove endpoints (backend Hamza), reviewed by
  Omar.

### Owner tools

- `sc-23/47/48` — Owner listing edit: `UpdateListingUC` + `PATCH /v1/listings/{id}`
  (backend), and the manage/edit listing screen (frontend). Reviewed by Hamza and
  Omar.
- `sc-53/54` — Listing image management. Backend merged. **Frontend blocked.**

### Favorites

- `sc-50/51/52` — Favorites backend endpoints and frontend provider plus saved page.
  Required a rebase and conflict resolution on PR #46.

### Brand

- Tahir mascot and logo identity exploration (Fatima).
- Rebrand application to Tahir's List with C2 lockup (Maryam).
- Mascot v2 art swap across screens (Fatima).
- Production favicon/app-icon set generated from `tahir-head.jpg` (Fatima).
- C2 lockup applied to site header and footer (Maryam).

### Seed data and research

- Seed restaurant research for NYC, Dallas, Austin, Toronto (Aisha).
- Seed hero photos manifest (Aisha).
- Photon geocoder adapter and seed ingestion with TDD (Omar/Hamza).
- `scripts/generate_seed_migration.py` to generate seed migrations.

## Timeline of the last week

**Established** from Kanban completion timestamps. This is the highest-resolution view
of what was happening at the end.

| Date/time | Owner | Card |
|-----------|-------|------|
| 09-01 17:39 | Maryam | Blocked: Verified certification display — wire to verification (sc-49) |
| 09-01 18:02 | Hamza | Expose certification metadata on listing read surface |
| 09-01 19:19 | Hamza | Refactor cutting filter to boolean hand-cut, drop machine-cut (sc-42) |
| 09-01 19:31 | Maryam | Hand-cut boolean UI (sc-42) |
| 09-01 20:52 | Maryam | QA fix: duplicate Unverified badge + desktop bottom nav (sc-185) |
| 09-02 03:26 | Hamza | Fix PR #54 review blockers, restore trust doc, reconcile sc-43 test (sc-119) |
| 09-02 09:18 | Omar | Partial-halal restaurant modeling in MVP (sc-119) |
| 09-02 17:46 | Hamza | Pickup/delivery service mode backend + search filter (sc-184) |
| 09-02 17:57 | Omar | Full address + map preview with pin (sc-187 frontend) |
| 09-02 17:59 | Maryam | Pickup/delivery display + filter UI (sc-184) |
| 09-02 20:03 | Omar | Structured address fields backend, TDD (sc-187) |
| 09-02 20:27 | Maryam | Render full comma-form address on detail (sc-187) |
| 09-02 21:18 | Omar | Code review PR #61 + #62 (sc-187) |
| 09-03 16:56 | Omar | Owner listing edit backend (sc-23/47/48) |
| 09-03 18:57 | Hamza | Rebase + resolve structured-address conflict (PR #61) |
| 09-03 21:10 | Hamza | Review PR #64 (sc-23 owner listing edit frontend) |
| 09-03 21:26 | Hamza | Review PR #64 (duplicate card) |
| 09-03 21:33 | Maryam | Owner listing edit frontend manage/edit screen (sc-23/47/48) |
| 09-03 23:03 | Omar | Review PR #65 (sc-53 listing image endpoints) |
| **09-09 21:31** | **Hamza** | **Listing image add backend — owner add/remove image endpoints (sc-53/54)** |

**Observations:**

- Several cards are assigned to the reviewer rather than the author (`Omar` on
  backend implementation cards). This is either mis-assignment or cards that were
  re-titled as review tasks. Do not read assignee as "who wrote the code" without
  checking git.
- `Review PR #64` appears twice on 09-03 (21:10 and 21:26). Duplicate cards happened.
  Expect some inflation in the ledger.
- The last completed card on 09-09 is five days after the last commit. That suggests
  a final review/merge cycle that did not produce new commits on `main`, or work
  completed on a branch that was never merged. **Check this before assuming the
  image endpoints are on `main`.**

## What remains unfinished

### The one blocked card

**Observed.** `t_63bc37f1` — "Listing image management (frontend) — add/remove image UI
on manage screen [sc-53/sc-54]", assignee Maryam, status blocked.

Body: "Depends on image backend `t_103b4c33`. Owner add/remove listing images in
manage UI, wired to new endpoints. AC: owner can add and remove images."

Block reason: **"Iteration budget exhausted (40/40) — task could not complete within
the allowed iterations."**

This is a worker-resource failure, not a product or dependency failure. The backend
it depends on is merged. Re-dispatch with a larger budget or split it into two cards
(add vs remove).

### Sprint 6 stories still open

**Established** from Shortcut iteration 191, "M1 Sprint 6 — Owner Tools: Manage Your
Listing", 2026-09-03 → 2026-09-10. Six stories:

| Story | Name | State |
|-------|------|-------|
| #53 | Add Image | In Development |
| #23 | Restaurant Information Management | In Development |
| #74 | Approve or Deny Restaurant Edits | Prioritization |
| #54 | Remove Image | Prioritization |
| #47 | Edit Unverified Restaurant | Prioritization |
| #48 | Edit Verified Restaurant | Prioritization |

The iteration end date has passed. It needs to be formally closed or extended. Two
stories say "In Development" but there is no active work — that is the reconciliation
you must do before planning anything new.

### Backlog themes

**Established** from the epics and stories seen in Shortcut. Names are quoted; story
counts are from the epic summaries where visible.

- **Epic 111** — 21 stories, largely search/browse and listing breadth.
- **Epic 112 — M1 Trust & Verification** — verification, edits, partial-halal.
- **Epic 113 — M1 Accounts & User Features** — auth, favorites, images.
- **Epic 114 — M1 Owner Tools** — owner edit, add/remove images.
- **Epic 115** — browser extensions (per README/ARCHITECTURE; not built).
- **Epic 196 — M1 Platform & Cost Hardening** — four open stories (#197–#200):
  downscale cert images before hosted AI, geocode cache table, quantize search coords
  + cache headers, pin JVM footprint. Plus one designed-in item (zero-egress storage
  via the S3 endpoint override).

I did not enumerate all seven epics by name; the Shortcut UI is the authority. The
counts above are what the API returned during this audit.

## How to read the ledger yourself

```bash
# Human-readable board
export -n HERMES_DELEGATED_CHILD_CONTEXT
hermes kanban list

# Full card detail from the database
python3 - <<'PY'
import sqlite3, datetime
db='~/.hermes/kanban/boards/halal-food-finder/kanban.db'
c=sqlite3.connect(db); c.row_factory=sqlite3.Row
for r in c.execute("select title,assignee,status,completed_at,result from tasks order by completed_at"):
    d=datetime.datetime.fromtimestamp(r['completed_at']).strftime('%Y-%m-%d %H:%M') if r['completed_at'] else '—'
    print(f"{d} | {r['assignee'] or '?':<8} | {r['status']:<8} | {r['title']}")
PY
```

The `result` column often contains the worker's final summary. It is the closest thing
to a per-task audit trail. Read it before assuming a card's outcome.

---

<!-- ===== 07-current-state-and-risks.md ===== -->

---
type: handoff-document
title: "07 — Current State and Risks"
description: "The honest audit: what is broken, stale, unverified, or risky in Tahir's List as of 2026-11-02, with severity and next actions."
tags: [tahirs-list, risks, audit, technical-debt, state-of-the-system, security]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: live system checks (docker, curl, git, sqlite) on 2026-11-02
    title: "Observed state"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Blocked card and completion timestamps"
  - resource: app.shortcut.com/halal-food-finder
    title: "Workflow state counts and open stories"
  - resource: ~/.hermes/profiles/omar/SOUL.md
    title: "Generic persona (debt)"
---

# 07 — Current State and Risks

This is the document I would want if I were taking over. It is deliberately blunt.
Nothing here is exaggerated, and where I am unsure I say so.

## The state in one paragraph

The codebase is substantially built and the backend is running. The preview frontend
is down. There is no production deployment. The work queue has been empty since
2026-09-09, so the automation has been running in circles for roughly eight weeks.
Shortcut and the Kanban disagree about what is in progress. There are no backups and
no CI. The biggest risk is not the code; it is that the operating state is illegible
and fragile.

## Risk register

Severity is about impact on users or data. Likelihood is about whether it bites you
without intervention.

| # | Risk | Severity | Likelihood | Evidence |
|---|------|----------|------------|----------|
| R1 | **No database backup or restore procedure** | Critical | High | No backup script/job found; data lives in Docker volumes `pgdata` and `miniodata` |
| R2 | **Preview frontend is down** | High | Certain (now) | `hff-frontend` restarting, exit 254; port 3000 refused; tunnel 502 |
| R3 | **Watchdog cannot detect a crash loop** | High | High | Liveness check reads `.State.Running`, true during restart cycles |
| R4 | **No CI pipeline** | High | Medium | No `.github/workflows/`; "green build" enforced manually |
| R5 | **No production host provisioned** | High | Certain | No SSH config, no server, no deployed artifact; only local Docker + dev tunnel |
| R6 | **Kanban/Shortcut drift** | Medium | Certain | 2 stories "In Development" with no active work; expired Sprint 6 |
| R7 | **Dirty working tree on a feature branch** | Medium | Certain | `git status` shows modified + untracked; branch is `wt/sc-187-detail-address-map` |
| R8 | **Seed image 403s** | Medium | Certain for affected listings | Backend logs show `FETCH_FAILED ... HTTP 403` from `lh3.googleusercontent.com` |
| R9 | **Uncommitted handoff bundle** | Medium | Certain | Files written to working tree only |
| R10 | **42 stale git worktrees** | Low | Certain | `.worktrees/` |
| R11 | **Omar's persona is the generic default** | Medium | Certain | `SOUL.md` is the Hermes default, not a security reviewer |
| R12 | **Secrets on disk in the working tree** | Medium | Low | `.cloudflared-token.txt` at repo root; gitignored and untracked, but present |
| R13 | **Dev credentials are defaults** | Medium | Certain | `halal/halal`, `minioadmin/minioadmin` in compose and launch script |
| R14 | **Image endpoints may not be on `main`** | Medium | Unknown | Last card completed 09-09, five days after last commit; verify before assuming |
| R15 | **Duplicate and mis-assigned Kanban cards** | Low | Certain | `Review PR #64` twice; backend cards assigned to Omar |
| R16 | **Open dependency vulnerabilities** | High | Certain | GitHub reports **47 Dependabot alerts** on the default branch: 6 critical, 13 high, 21 moderate, 7 low (observed 2026-11-02) |

### R1 — No backups (fix first)

The Postgres data volume (`halal-food-finder_pgdata`) and MinIO volume
(`halal-food-finder_miniodata`) hold the seeded listings, users, and any uploaded
certificates. I found no backup job, no snapshot script, and no documented restore
procedure. If the volumes are pruned, or the machine is rebuilt, that data is gone.

Minimum viable fix: a nightly `pg_dump` plus a `mc mirror` of the MinIO bucket to a
second location, and a restore runbook you have actually tested once. Do this before
you change anything else about the deployment.

### R2 — Preview frontend down

Symptoms and the full fix procedure are in
[05-operations-and-infrastructure.md](05-operations-and-infrastructure.md). Short
version: recreate the containers from a clean `main`, confirm backend health first,
then rebuild the frontend. Read the frontend logs if it still exits 254.

### R3 — Watchdog blind spot

The 15-minute watchdog has run ~3,925 times and reported `ok` while the stack was
broken. Its liveness check is fooled by a crash-looping container. It also swallows
`git checkout main` failures (`2>/dev/null`) and has no `set -e`, so it can redeploy
whatever branch happens to be checked out. Fix both: check HTTP health or restart
counts, and make failures loud.

### R4 — No CI

Every "build green" claim in the PR history rests on someone running the scripts and
saying so. Add a minimal GitHub Actions workflow running `backend-test.sh` and
`frontend-test.sh` on PRs and on `main`. This is the highest-leverage process fix
available and it takes very little effort.

### R5 — No production host

Hetzner was ratified but never provisioned. There is no server, no Jib image pushed to
a registry, no DNS pointing at a production origin. The product has never been publicly
deployed. Do not describe it as live. The migration path is documented and cheap
(Jib images + env-var connection strings; `pg_dump`/restore for the database), but it
has not been executed.

### R6 — Kanban/Shortcut drift

Shortcut says #53 and #23 are "In Development". The Kanban has no running cards. Sprint
6's end date (2026-09-10) has passed. Reconcile before planning: either resume the work
or move the stories back to Prioritization and close the iteration. A board that lies
is worse than an empty board.

### R7 — Dirty tree on a feature branch

The repo is on `wt/sc-187-detail-address-map`, not `main`, with modified and untracked
files. Both deploy scripts assume `main`. The watchdog's `git checkout main` fails
silently against a dirty tree. Get clean and stay clean; this is a recurring footgun.

### R8 — Seed images returning 403

The backend's seed ingest logs repeated HTTP 403s from Google's image CDN for several
seeded restaurants (Caspian Grill, Halal Bros, Halal Wings). Those URLs are dead or now
require auth. Listings still render, but without photos. Re-host the images in MinIO or
replace the URLs. This is content debt, not a code bug — and it is a visible quality
problem on a product whose whole value proposition is trust.

### R9 — Uncommitted handoff

This bundle is in the working tree only. If the machine is rebuilt or the branch is
discarded, it is lost. Commit it on a clean branch first.

### R10 — Worktree sprawl

42 directories under `.worktrees/`. They consume disk and make `git` operations slower.
Some may contain unmerged work. Check for unmerged commits (`git log main..<branch>`)
before deleting, then prune.

### R11 — Omar's persona debt

`~/.hermes/profiles/omar/SOUL.md` is 876 bytes of the generic Hermes Agent
prompt plus a note to review diffs rather than whole files. The security-reviewer role
lived in AGENTS.md and in task assignment prompts, not in the persona. If you keep Omar,
rewrite the persona. If you collapse to one agent, carry the security rules explicitly
so they do not evaporate with the profile.

### R12 — Secrets on disk

`.cloudflared-token.txt` sits at the repo root. It is gitignored and **not tracked**
(verified with `git ls-files --error-unmatch`), so it has not leaked to GitHub. But it is
a live credential in a working tree that also contains untracked logs and artifacts. Move
it to a secret store or at minimum keep it out of any archive or bundle you share.

### R13 — Default dev credentials

`halal/halal` and `minioadmin/minioadmin` are fine on localhost. They are not fine on a
public host. Change them when you provision production, and do not copy the compose file
to the server unchanged.

### R14 — Unverified: are the image endpoints on `main`?

The last Kanban card completed 2026-09-09, but the last commit is 2026-09-04. That gap
could mean the final work merged without a new commit on `main`, or it completed on a
branch that never merged. Before telling anyone the owner image endpoints exist in
production, verify: `git log main --oneline | head`, `git branch --contains <sha>`, and
check the controller on `main` rather than the working tree.

### R15 — Duplicate and mis-assigned cards

`Review PR #64` appears twice on 2026-09-03. Several backend implementation cards are
assigned to Omar (the reviewer) rather than Hamza (the author). The ledger is directionally
accurate but not clean. Do not use assignee counts as a performance measure without
checking git authorship.

### R16 — Open dependency vulnerabilities

**Observed.** GitHub reported **47 vulnerabilities on the default branch** at push time:
**6 critical, 13 high, 21 moderate, 7 low** (Dependabot). I did **not** triage them, so treat
the individual severities as unverified until reviewed; the count and distribution are what
GitHub reported.

Next action: open the repository's **Security → Dependabot alerts** page and triage by
runtime exposure, not by raw count. Many alerts are likely in development-only dependencies
and will not be reachable in production, but "likely" is not a security decision. Prioritize
anything reachable from the backend HTTP surface or the Next.js server, then the rest.
This is a standing maintenance task, not a one-off patch.

## Things I could not verify

Stated plainly so you do not inherit false confidence:

- **Whether the eight-week dormancy was intentional.** I found no note recording a pause
  decision. The cron jobs kept running, which suggests drift rather than a deliberate
  freeze.
- **Whether `dev.tahirslist.com` is meant to be public.** It is reachable but broken. No
  doc states its intended audience.
- **Whether production data exists anywhere.** If a Hetzner box was ever provisioned and
  later destroyed, I found no trace. Assume no production data exists.
- **Whether the browser extensions are still in M1 scope.** ARCHITECTURE.md says MVP
  scope; no extension code exists in the repo. That is a founder question.
- **Whether all seven Shortcut epics are still current.** I saw seven; I enumerated five
  by name (111, 112, 113, 114, 196) plus 158 referenced in the sprint prompt. The rest
  need a human read of Shortcut.
- **The true state of `main` versus the feature branch.** The working tree is on
  `wt/sc-187-detail-address-map`, so anything I inspected in the tree may not match
  `main`. Re-verify against `main` before acting.

## What is *not* a risk (so you do not over-correct)

- **The backend architecture.** Hexagonal modules, clear dependency direction, 68 test
  files, TDD throughout. It is in good shape. Do not rewrite it.
- **The domain model.** The agreed model in ARCHITECTURE.md is coherent and matches the
  implementation closely enough. Do not redesign it.
- **The design system.** Tokens, trust components, and brand assets are documented and
  consistent. Do not restart the design.
- **The security review trail.** Auth was reviewed statically by Omar and attacked
  dynamically by Yusuf. That is better than most projects at this stage. Preserve the
  practice.
- **The product thinking.** The PRD, use cases, personas, and open questions are thorough.
  The problem was never the thinking.

## Priority order

If you do nothing else, do these in order:

1. Commit this handoff bundle on a clean branch.
2. Back up Postgres and MinIO, and write a restore runbook.
3. Restore the preview (clean `main`, recreate backend then frontend, verify end to end).
4. Fix the watchdog's liveness check and make it fail loudly.
5. Add a minimal CI workflow for both test scripts.
6. Triage the Dependabot alerts (47 open, 6 critical) by runtime exposure.
7. Reconcile Shortcut to reality and close or extend Sprint 6.
8. Clear the blocked image-management card.
9. Decide the operating model and delete the automation you are not using.

---

<!-- ===== 08-roadmap-and-open-questions.md ===== -->

---
type: handoff-document
title: "08 — Roadmap and Open Questions"
description: "What the team hoped to accomplish next: Sprint 6 scope, backlog themes, cost-hardening work, and the product decisions that still require the founder."
tags: [tahirs-list, roadmap, backlog, shortcut, open-questions, product-decisions]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: app.shortcut.com/halal-food-finder/iteration/191
    title: "M1 Sprint 6 — Owner Tools: Manage Your Listing"
  - resource: app.shortcut.com/halal-food-finder
    title: "Epics and Prioritization backlog"
  - resource: ARCHITECTURE.md
    title: "Part 2 — unresolved decisions and open product questions"
  - resource: ~/.hermes/profiles/adnan/cron/jobs.json
    title: "Sprint planner prompt (epic scope and planning rules)"
---

# 08 — Roadmap and Open Questions

## Where the roadmap stood

**Established.** Milestone 1 was in progress. The README describes it as "MVP trust and
verification vertical slice" plus the surrounding product surface. The sprint planner's
instructions scoped M1 to **epics 111–115 and 158**, plus the later cost-hardening epic
196. The verification vertical slice plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`) framed the first
coherent slice as: add a restaurant → submit certification → AI + human review → users
see verified status.

That spine is built. The remaining M1 work is breadth and owner tooling.

## Sprint 6 — Owner Tools: Manage Your Listing

**Established** from Shortcut iteration 191. Window 2026-09-03 → 2026-09-10 (expired).
Six stories:

| Story | Name | Epic | State at handoff |
|-------|------|------|------------------|
| #53 | Add Image | 113 M1 Accounts & User Features | In Development |
| #23 | Restaurant Information Management | 114 M1 Owner Tools | In Development |
| #74 | Approve or Deny Restaurant Edits | 112 M1 Trust & Verification | Prioritization |
| #54 | Remove Image | 114 M1 Owner Tools | Prioritization |
| #47 | Edit Unverified Restaurant | 112 M1 Trust & Verification | Prioritization |
| #48 | Edit Verified Restaurant | 114 M1 Owner Tools | Prioritization |

**What this means in practice:** owner listing edit is built (backend and frontend,
`sc-23/47/48`), and owner image add/remove is built on the backend (`sc-53`). What is
missing is the **frontend UI** to add and remove images on the manage screen. That is
the blocked Kanban card. Stories #74 and the remaining edit flows are still in the
backlog.

**Recommended next slice:** finish owner tooling as one coherent unit — image add/remove
UI, the approve/deny restaurant edits flow, and the remaining edit paths. It is a natural
milestone, it has clear acceptance criteria, and the backend is already there. Do not
start browser extensions before owner tools are complete; extensions multiply surface
area without deepening the core loop.

## Backlog themes

**Established** from the epic summaries returned during this audit. Names are quoted
where known. The Shortcut UI is the authority; counts are approximate.

- **Epic 111 —** 21 stories. Search and browse breadth, listing details, and related
  product surface. This is the largest epic and the likely home of remaining M1 breadth.
- **Epic 112 — M1 Trust & Verification.** Verification, restaurant edits, partial-halal,
  approval flows. Includes #47 and #74.
- **Epic 113 — M1 Accounts & User Features.** Auth, favorites, images. Includes #53.
- **Epic 114 — M1 Owner Tools.** Owner edit and image management. Includes #23, #48, #54.
- **Epic 115 —** browser extensions for Google and Yelp. No code exists. In MVP scope per
  ARCHITECTURE.md.
- **Epic 158 —** referenced in the sprint planner's scope instruction; I did not read its
  contents. Check it in Shortcut before planning.
- **Epic 196 — M1 Platform & Cost Hardening.** Founder-directed cost hedge, created
  2026-09-04. Four open stories plus one designed-in item. See below.

**Backlog volume:** ~35 stories in Prioritization. That is roughly three to four
sprints' worth at the observed pace. Treat it as a menu, not a commitment.

## Epic 196 — Cost hardening (founder-directed)

**Established** from the epic description. Five geospatial/media cost traps the founder
raised to bake into the plan:

1. **Zero-egress object storage** (Cloudflare R2 or Backblaze B2) instead of paying egress.
   *Already designed in* at the code level via the S3 endpoint override — `S3ImagePort`
   takes a configurable endpoint, so R2 is a config change.
2. **Downscale certificate images before hosted vision AI** (story #197). Reduces token
   cost and upload hygiene risk.
3. **Geocode cache table** (story #198). Avoids re-billing Google Geocoding for the same
   address.
4. **Quantize search coordinates + cache headers** (story #199). Reduces payload and CDN
   cost.
5. **Pin JVM footprint for low-cost compute** (story #200). Keeps the VPS at CX-class
   size.

None of these were started. They are small, well-scoped, and directly reduce run-rate.
They are good candidates for a single "hardening" sprint before scaling usage.

## Open product questions requiring the founder

**Unresolved.** These are product decisions, not engineering choices. Do not answer them
in code without an explicit founder decision. They are carried from ARCHITECTURE.md
Part 2 and the PRD's "Open Questions" and "Edge Cases."

### 1. Partial-halal semantics (U-14)

**Status:** Confirmed **in MVP scope** by the founder on 2026-08-29. Exact modeling still
open.

The verification tag currently implies whole-restaurant status. Partial-halal needs its
own representation: a field, a tag, a filter, or some combination. The code has a
partial-halal representation (`V18` migration, `sc-119` cross-contamination gate), but the
product semantics were never fully ratified. Questions to resolve:

- Is "partial halal" an attribute of the restaurant, the menu, or both?
- Does a partial-halal restaurant qualify for verification? If so, what does the badge
  mean?
- How should search and filters treat partial-halal restaurants — included, excluded, or
  separately labelled?
- How is cross-contamination risk communicated without implying a guarantee?

### 2. Alcohol-served attribute (U-15)

**Status:** In MVP scope; field and filter exist (`V11`, `sc-118`).

Open questions:

- Should "serves alcohol" affect verification status, or is it purely informational?
- Is it a filter users can apply, a warning badge, or both?
- Does the copy need to distinguish "serves alcohol" from "alcohol in food"?

### 3. Certification expiry and revocation

**Status:** Open flag. Founder flagged it on 2026-08-29 and it was never resolved.

Questions:

- Should the verified page display the certificate's expiry date?
- What happens to the verified badge when a certificate lapses — auto-downgrade to
  unverified, show an "expired" state, or notify the owner?
- What is the revocation flow if a certificate is withdrawn or found invalid? Who can
  revoke, and is the listing's history retained?

This is the highest-stakes open question for trust. A verified badge that stays green
after the certificate expires is worse than no badge.

### 4. Kosher restaurants

**Status:** Unresolved in the PRD. Should kosher establishments be listed? If yes, does
the verification process differ?

### 5. Beyond restaurants

**Status:** Unresolved. Meat shops, grocers, food trucks, caterers? The listing model is
restaurant-shaped today. Broadening it is a product and schema decision.

### 6. Browser extensions in M1

**Status:** Ambiguous. ARCHITECTURE.md says in MVP scope; no code exists. Confirm with the
founder whether they are still M1 or have moved to a later milestone. If they stay in M1,
they are the largest unstarted workstream and should be planned as such.

### 7. Deferred by the PRD — do not build

Menu-item entries and analytics date-range customizability. The PRD explicitly deferred
them. Leave them deferred unless the founder changes the decision.

## The definition of "done" for M1

**Inferred, not established.** I did not find a single written definition of done for M1.
Based on the PRD pillars and the epics in scope, M1 done probably means:

- A user can search by location and filter by hand-cut, price, cuisine, rating, and
  distance, and get correct results.
- A user can add a restaurant, and it appears as unverified.
- An owner can claim a listing, consent to AI review, upload proof and a certificate.
- The AI suggests a verdict; a human VC approves or denies; the listing becomes verified
  and shows the certificate and badge.
- An owner can edit their listing and add/remove images.
- Favorites work.
- Public listing pages are crawlable (SEO decision).
- The browser extensions exist, if they remain in scope.

**This definition should be written down and ratified by the founder before the next
sprint is planned.** It is currently a gap. Without it, "M1" will keep absorbing new work.

## Planning rules that were in force

**Established** from the sprint planner prompt. These were founder-set and are worth
keeping or consciously replacing:

- The sprint planner runs with full autonomy and does not ask the founder questions.
- A sprint is diagnosed **from work, not the calendar**. An end date alone is not evidence
  the sprint is alive.
- A sprint is **exhausted** when no card is running and no ready/todo card is executable.
  Founder-gated cards do not count as executable work.
- If exhausted early, close it early and open the next one. Keeping work moving beats the
  calendar.
- New sprint naming: `M1 Sprint N — <Theme>`, with a real ~7-day window starting today.
- Plan a coherent milestone across backend, frontend, edge cases, and review gates. Narrow
  slices get burned in a day.
- Scope only from real Shortcut stories in backlog epics that are not done. **Never invent
  features.** Own-initiated tickets are bug fixes only.
- Reconcile against the repo before queueing: inspect what is already built so you do not
  re-card done work. Verify the foundation is green before dispatching on top of it.
- Every story goes in its epic **and** its iteration (founder rule).

**One caution:** "do not ask the founder questions" is fine for sequencing but wrong for
the open product questions above. Those need the founder. Autonomy should not extend to
inventing product semantics.

## My recommendation for the first two weeks

Stated as a recommendation, not a requirement:

**Week 1 — stabilize.**

1. Commit this handoff on a clean branch.
2. Back up Postgres and MinIO; write and test a restore runbook.
3. Restore the preview end to end and verify it.
4. Fix the watchdog's liveness check and make failures loud.
5. Add CI for both test scripts.
6. Reconcile Shortcut: close or extend Sprint 6, move stale "In Development" stories.

**Week 2 — finish the core loop.**

7. Clear the blocked image-management card (frontend add/remove UI).
8. Finish the owner-tools slice: approve/deny restaurant edits plus remaining edit flows.
9. Write the M1 definition of done and get it ratified.
10. Get the founder's decisions on certification expiry/revocation and partial-halal
    semantics, because both affect the verification UX you will build next.

Do not start browser extensions, dark theme, or cost hardening until the core loop is
demonstrably working end to end in a preview a human can click through. The product's
value is trust; a working verification loop is the proof.

---

<!-- ===== 09-references-and-glossary.md ===== -->

---
type: handoff-document
title: "09 — References and Glossary"
description: "Lookup tables: glossary, Shortcut story IDs, file pointers, commands, cron job IDs, URLs, and where to find the detailed documentation."
tags: [tahirs-list, glossary, references, story-ids, commands, index]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: .
    title: "Repository root"
  - resource: app.shortcut.com/halal-food-finder
    title: "Shortcut workspace"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/
    title: "Kanban board data"
---

# 09 — References and Glossary

## Glossary

| Term | Meaning |
|------|---------|
| **Tahir's List** | The product name. Pivot from "Halal Food Finder" (HFF). Domain `tahirslist.com`. |
| **HFF** | Halal Food Finder. The original project name, still used in paths, cron job names, and some code. Same product. |
| **Amanah** | Trust / communal responsibility. Used in AGENTS.md to mean: report status, progress, and defects honestly; never exaggerate or hide known issues. |
| **Hand-cut** | Boolean listing attribute. Whether meat is hand-cut (Zabiha). The product has **no machine-cut concept** after `sc-42`. |
| **Partial-halal** | A restaurant that is not wholly halal (e.g. some menu items or shared kitchen). In MVP scope; exact semantics unresolved (U-14). |
| **Alcohol-served** | Listing attribute: whether the establishment serves alcohol. In MVP scope; semantics partly open (U-15). |
| **Verification** | The formal process by which an owner claims a listing, consents to AI review, uploads proof + certificate, and a human committee approves or denies. |
| **VC** | Verification Committee. A role on an existing account that reviews verification requests. |
| **IC** | Issues Committee. A role that reviews reported issues. |
| **VRO** | Verified Restaurant Owner. An owner verified as the owner of a listing. |
| **ET** | Extension Trigger. The event the browser extensions listen for. |
| **Verification seam** | The `VerificationProvider` port plus adapters. Lets the hosted-AI provider be swapped without changing domain code. |
| **Hosted AI** | The vision model used to suggest a verdict on a certificate image. Default: Gemini 2.5 Flash, paid tier. |
| **Conservative verdict policy** | The rule that the AI suggests a verdict cautiously; uncertain cases go to human review rather than auto-approving. |
| **Human-in-the-loop** | Mandatory human review of AI verification suggestions. Required by provider terms and by founder decision. |
| **Hexagonal architecture** | Ports-and-adapters layout: `:domain → :application → adapters → :bootstrap`. Domain must not depend on frameworks. |
| **Seam** | An interface boundary where an implementation can be swapped (e.g. image storage, geocoder, verification provider). |
| **M1** | Milestone 1. The MVP scope. Epics 111–115 and 158, plus cost-hardening epic 196. |
| **SC-###** | A Shortcut story identifier, e.g. `SC-131`. Used as the commit prefix. |
| **t_xxxxxxxx** | A Hermes Kanban task ID, e.g. `t_63bc37f1`. |
| **Kanban** | Hermes Kanban. The execution queue (what to do), distinct from Shortcut (what the product needs). |
| **Worktree** | A separate checkout of the same git repo, used so multiple tasks can work in parallel without branch conflicts. `.worktrees/` holds 42 historical ones. |
| **Coordination gate** | The 30-minute cron job that merges PRs, dispatches work, and unblocks cards when the action fingerprint changes. |
| **Watchdog** | The 15-minute script-only cron job that redeploys the preview when `main` moves or a container dies. |
| **Preview** | The local Docker stack exposed at `https://dev.tahirslist.com` via a stable Cloudflare tunnel. Not production. |
| **Live config** | `APP_S3_ENDPOINT` set and `APP_SEED_INGEST_ENABLED=true`. Enables the MinIO-backed `ImagePort` and the seed-photo ingest runner. |
| **Seed ingest** | Boot-time runner that downloads seed restaurant photos into MinIO. Currently failing with HTTP 403 on some URLs. |
| **OKF** | Open Knowledge Format. Markdown + YAML frontmatter knowledge bundles. This handoff uses its conventions. |

## Shortcut story ID index

**Established** from the Kanban ledger titles and Shortcut. This is the practical
cross-reference for the IDs you will see in commits and cards.

| ID | What it is |
|----|------------|
| SC-39 | Create Account |
| SC-40 | Log In |
| SC-42 | Filter by food cutting method → collapsed to boolean hand-cut |
| SC-43/44 | Price and cuisine filters (AND/OR chaining) |
| SC-45/72 | Rating and distance filters |
| SC-46 | Owner claim a listing |
| SC-47 | Edit Unverified Restaurant |
| SC-48 | Edit Verified Restaurant |
| SC-49 | Verified certification display — wire to verification |
| SC-50/51/52 | Favorites (backend endpoints, frontend provider, saved page) |
| SC-53 | Add Image (owner listing images) |
| SC-54 | Remove Image (owner listing images) |
| SC-73 | Verification Committee approve/deny + promotion + RBAC |
| SC-74 | Approve or Deny Restaurant Edits |
| SC-116 | Scaffold project repo (backend + frontend skeletons) |
| SC-117 | Verification provider seam (hosted AI + human review) |
| SC-118 | Alcohol-served attribute |
| SC-119 | Partial-halal modeling + cross-contamination index gate |
| SC-120 | AI consent on the verification claim |
| SC-131 | JWT verification filter |
| SC-132 | Server-side logout |
| SC-133 | Refresh-token HttpOnly cookie + session lifecycle |
| SC-134 | Fail-fast config, keys, error handling, security headers |
| SC-135 | Auth test-gap closure + QA conventions |
| SC-138 | Add-listing vertical — external services gate review (geocoding, POI/listing data sources) |
| SC-155 | Seed ingest |
| SC-157 | Responsive restaurant imagery: MinIO variants + frontend loading |
| SC-159 | Tahir mascot + logo identity |
| SC-164 | Rebrand codebase package names to Tahir's List |
| SC-171 | Live listing read |
| SC-178 | Seed photo follow-up: resolve UNRESOLVED + replace 403 URLs |
| SC-180 | next/image optimizer allow `/v1` image URLs |
| SC-183 | Responsive multi-width `srcset` for card thumbs |
| SC-184 | Pickup/delivery service mode + filter |
| SC-185 | QA fix: duplicate Unverified badge + desktop bottom nav |
| SC-187 | Structured address fields + map preview |
| SC-196–200 | Cost hardening epic and its four open stories |

> IDs are from commit messages, Kanban titles, and Shortcut search results. If a number
> is not in this table, look it up in Shortcut rather than guessing.

## Where things live

| What you need | Where it is |
|---------------|-------------|
| Product overview and personas | `README.md` |
| Agreed architecture + open decisions | `ARCHITECTURE.md` |
| Engineering rules (read before coding) | `AGENTS.md` |
| MVP vertical slice plan | `.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md` |
| Design specs and tokens | `docs/design/` (14 files) |
| QA assessment and conventions | `docs/qa/` (2 files) |
| External services and seed research | `docs/research/` (3 files) |
| External services review | `docs/reviews/` |
| Auth security review | `docs/security/auth-security-review-2026-08-29.md` |
| Brand decisions and character bible | `brand/tahir/BRAND-DECISIONS.md`, `brand/tahir/CHARACTER.md` |
| Mascot art and lockups | `brand/tahir/` |
| Backend source | `backend/` (hexagonal modules) |
| Database migrations | `backend/adapters/persistence/src/main/resources/db/migration/` |
| OpenAPI spec | `backend/openapi/` |
| Frontend source | `frontend/src/` |
| Build and run scripts | `scripts/` |
| Kanban board data | `~/.hermes/kanban/boards/halal-food-finder/kanban.db` |
| Cron job definitions | `~/.hermes/profiles/adnan/cron/jobs.json` |
| Operations scripts | `~/.hermes/profiles/adnan/scripts/` |
| This handoff bundle | `docs/handoff/` |

## Commands cheat sheet

### Build and test (Docker only; no host toolchains)

```bash
cd /path/to/tahirs-list

# Backend: full build + tests
./scripts/backend-test.sh

# Backend: tests only (faster)
./scripts/backend-test.sh test-only

# Frontend: clean install + build
./scripts/frontend-test.sh

# Frontend: reuse existing install
./scripts/frontend-test.sh dev-install
```

### Run the stack

```bash
# Backing services (PostGIS + object store)
docker compose -f backend/docker-compose.yml up -d

# Backend with the live profile (S3 + seed ingest)
bash scripts/backend-live.sh

# Frontend (inline command from the watchdog)
docker rm -f tahirs-list-frontend
docker run -d --name tahirs-list-frontend --restart unless-stopped -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app -w /app -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"
```

### Health checks

```bash
curl -s http://localhost:8080/actuator/health        # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
curl -s -o /dev/null -w '%{http_code}\n' https://dev.tahirslist.com
docker ps --filter name=tahirs-list-
docker logs tahirs-list-backend --tail 50
docker logs tahirs-list-frontend --tail 50
```

### Kanban

```bash
export -n HERMES_DELEGATED_CHILD_CONTEXT   # required in agent shells
hermes kanban list
hermes kanban boards list
hermes kanban dispatch
```

### Git

```bash
git log --oneline -20
git status --porcelain
git branch -a --sort=-committerdate | head
gh pr list --repo Losers-Lab/tahirs-list --state open
gh pr view <n> --repo Losers-Lab/tahirs-list --json reviewDecision,reviews
```

### Shortcut (via MCP tools)

Available tools include `get_whoami`, `list_epics`, `list_iterations`,
`get_current_iterations`, `get_iteration`, `search_stories`, `get_story`,
`create_story`, `update_story`, `create_story_comment`, `list_story_comments`,
`list_workflow_states`, `list_labels`, `list_members`, and more.

The search query language supports operators such as `state:"In Development"`,
`iteration:191`, `epic:112`, `is:done`, `is:started`, `is:blocked`, `has:pr`,
`created:2026-01-01..2026-02-01`, `updated:>2026-01-01`, and `!is:done`.

## Cron job IDs and schedules

| ID | Name | Schedule |
|----|------|----------|
| `ee7b25152cb2` | HFF coordination gate (daytime) | `0,30 0,9-23 * * *` |
| `f37d47352a20` | HFF coordination gate (overnight) | `0 3,6 * * *` |
| `827f0630945f` | HFF sprint plan + execute | `0 9,17 * * *` |
| `d91459805654` | HFF deploy watchdog | `*/15 * * * *` |

Jobs are defined in `~/.hermes/profiles/adnan/cron/jobs.json`. The watchdog is
`no_agent: true` (script only). The others are LLM jobs gated by a monitor script
(`hff_check.sh` or `hff_sprint_gate.sh`).

**Note:** all jobs reported `last_status: ok` while the stack was down. "Ok" means the job
ran, not that the system is healthy. Do not treat it as a health signal.

## URLs

| What | URL |
|------|-----|
| Public preview | `https://dev.tahirslist.com` |
| Production (not provisioned) | `tahirslist.com` |
| GitHub repo | `https://github.com/Losers-Lab/tahirs-list` |
| Shortcut workspace | `https://app.shortcut.com/halal-food-finder` |
| Backend API (local) | `http://localhost:8080` |
| Frontend (local) | `http://localhost:3000` |
| MinIO console (local) | `http://localhost:9001` |

## Key documents to read in the repo

In this order, if you are new:

1. `README.md` — product overview and personas.
2. `AGENTS.md` — engineering rules and context.
3. `ARCHITECTURE.md` — agreed architecture and open decisions.
4. `.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md` — the slice plan.
5. `docs/design/tokens.md` — design system.
6. `docs/security/auth-security-review-2026-08-29.md` — security posture.
7. `docs/qa/auth-qa-assessment-and-testing-conventions.md` — QA conventions.
8. `docs/research/external-services-brief-2026-08-29.md` — service choices and costs.
9. `brand/tahir/BRAND-DECISIONS.md` — brand decisions.
10. This bundle — `docs/handoff/index.md`.

## People and escalation

| Topic | Who |
|-------|-----|
| Product, scope, brand, money, open questions | The founder (Arham) |
| Backend, schema, migrations, PostGIS | Hamza's prior work; read the code and AGENTS.md |
| Frontend and design intent | Maryam (frontend), Fatima (design) |
| Security posture | Omar's reviews in `docs/security/` and on PRs |
| QA conventions | Yusuf's work in `docs/qa/` |
| External services and data provenance | Aisha's briefs in `docs/research/` |

**Reminder:** the specialist agents may or may not be retained in the synchronous model.
If they are not, their rules still apply — read their personas and carry the rules forward
explicitly rather than assuming a profile will enforce them.

---

<!-- ===== 10-fresh-machine-setup.md ===== -->

---
type: handoff-document
title: "10 — Fresh Machine Setup (Docker, from zero)"
description: "A complete, machine-independent procedure to go from a brand-new computer with nothing installed to a running Tahir's List stack: Docker setup per OS, backing services, backend, frontend, smoke tests, teardown, and troubleshooting."
tags: [tahirs-list, setup, onboarding, docker, docker-compose, wsl2, macos, linux, postgis, minio, ktor, sveltekit]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
audience: "A developer on a computer that has never run this project."
sources:
  - resource: backend/docker-compose.yml
    title: "PostGIS + MinIO service definitions"
  - resource: scripts/backend-test.sh
    title: "Containerized backend build/test"
  - resource: scripts/frontend-test.sh
    title: "Containerized frontend build/test"
  - resource: scripts/backend-live.sh
    title: "Live-profile backend launcher"
  - resource: backend/bootstrap/src/main/resources/application.yml
    title: "Default datasource and server config"
  - resource: backend/bootstrap/src/main/resources/application-live.yml
    title: "Live profile: S3 storage + seed ingest"
  - resource: frontend/next.config.ts
    title: "API proxy target"
  - resource: AGENTS.md
    title: "Containerized-execution mandate"
---

# 10 — Fresh Machine Setup

This document replaces the assumption that you are working on the machine the project
was built on. Nothing here depends on a particular user account, home directory, hostname,
or Hermes installation. Every path is relative to the repository root unless it starts
with `~/`.

Read [05 — Operations and Infrastructure](05-operations-and-infrastructure.md) **after**
this document if you want to understand how the original machine was wired. That document
describes the old host, including automation that does not travel with the repo. This one
gets you running.

## What you are setting up

Five things run. Four are containers; the fifth is a container that runs your build.

```
                          your browser
                               │
                               ▼
                    ┌──────────────────────┐
                    │ tahirs-list-frontend │  Next.js 16 on node:22, port 3000
                    │ (container)          │  proxies /v1/* to the backend
                    └──────────┬───────────┘
                               │ http://tahirs-list-backend:8080
                               ▼
                    ┌──────────────────────┐
                    │ tahirs-list-backend  │  Kotlin / Ktor 3, port 8080
                    │ (container)          │  runs ./gradlew :bootstrap:run
                    └─────┬──────────┬─────┘
                          │          │
              jdbc ───────┘          └─────── s3
                          ▼                     ▼
              ┌────────────────────┐  ┌───────────────────┐
              │ tahirs-list-postgis│  │ tahirs-list-minio │
              │ PG17 + PostGIS     │  │ S3-compatible     │
              │ port 5432          │  │ ports 9000/9001   │
              └────────────────────┘  └───────────────────┘
```

All five join one Docker bridge network, `tahirs-list_default`. Containers address
each other by **service name** (`postgis`, `minio`), not by `localhost`. The browser only
ever talks to port 3000; Next.js rewrites `/v1/*` to the backend so the browser stays
same-origin and the backend needs no CORS configuration.

## What you need before you start

### Hardware

| Resource | Minimum | Comfortable |
|---|---|---|
| RAM | 8 GB | 16 GB |
| Free disk | 15 GB | 30 GB |
| CPU | 2 cores | 4 cores |

The first build is the expensive part: the Gradle dependency cache and the npm cache are
several GB. After that, incremental runs are cheap.

### Software

| Tool | Why | Notes |
|---|---|---|
| **Docker Engine 25+** (Docker Desktop 4.28+) | Everything runs in containers | **Inferred** minimum from the Testcontainers API pin (`api.version=1.44`) noted in `backend/README.md`. Newer is better. |
| **Docker Compose v2** | Backing services | Ships with Docker Desktop; on Linux install the plugin. |
| **Git** | Clone and branch | Any recent version. |
| **A terminal** | All commands below | On Windows, use the **WSL2** shell, not PowerShell. |
| `gh` (optional) | Pull requests, issues | `https://cli.github.com` |
| `cloudflared` (optional) | Public preview URL | Only if you want a public hostname. |

### Explicitly NOT needed on the host

**Do not install a JDK, Gradle, Node, or npm.** `AGENTS.md` makes containerized execution
mandatory: no language toolchains on the host, and no `apt install` / `sdkman` / `brew install`
for a runtime. Every build and test runs inside a container. If a toolchain seems missing,
that is a signal to containerize, not to install.

### Accounts and access

- Read/write access to `https://github.com/Losers-Lab/tahirs-list`.
- The **Cloudflare tunnel token** for `dev.tahirslist.com` — only if you want the public
  preview. It is **not** in the repository and is not recoverable from the code. Ask the
  founder.
- Shortcut access (`app.shortcut.com/halal-food-finder`) for product requirements.

---

## Step 0 — Install Docker for your operating system

### Windows 10/11 (recommended path: WSL2 + Docker Desktop)

1. Install **WSL2** and a distribution:
   ```powershell
   wsl --install -d Ubuntu
   ```
   Reboot if prompted, then open Ubuntu and create your Linux user.

2. Install **Docker Desktop for Windows**. In **Settings → Resources → WSL Integration**,
   enable integration for your Ubuntu distribution. Docker Desktop must be running for any
   of the commands below to work.

3. **Clone the repository inside the WSL filesystem**, not on the Windows drive:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```
   A clone under `/mnt/c/...` works but is markedly slower for npm/Gradle builds and can
   corrupt the executable bit on `gradlew`. Use the Linux home directory.

4. Turn off CRLF translation **before** building, or `gradlew` will fail with
   `bad interpreter: /bin/sh^M`:
   ```bash
   git config --global core.autocrlf false
   git rm --cached -r . >/dev/null 2>&1 || true
   git reset --hard
   ```
   Run the `git config` line *before* the clone if you can; it is simpler.

### macOS

1. Install **Docker Desktop for Mac** (Apple Silicon or Intel build as appropriate) and start it.
2. If the PostGIS image has no native build for your architecture, Docker Desktop's
   **Settings → General → "Use Rosetta for x86_64/amd64 emulation"** lets it run the amd64
   image. Alternatively export `DOCKER_DEFAULT_PLATFORM=linux/amd64` in the shell you build
   from. Expect slower builds under emulation.
3. Clone anywhere in your home directory:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```

### Linux (Ubuntu/Debian)

1. Install Docker Engine and the Compose plugin:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"
   ```
   Log out and back in so the group change applies.

2. Verify:
   ```bash
   docker version
   docker compose version
   ```

3. Clone:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```

> **Linux note:** `scripts/backend-test.sh` mounts `/var/run/docker.sock`. That exists
> natively on Linux and inside WSL2 with Docker Desktop integration. It does **not** exist
> in a plain container or on macOS at that exact path — see Troubleshooting.

---

## Step 1 — Sanity-check Docker before building anything

Run these from the repository root. Fix failures here rather than debugging them later.

```bash
docker version                 # client AND server must both print
docker compose version         # v2.x
docker info --format '{{.ServerVersion}}'
docker run --rm hello-world    # proves the daemon can pull and run
```

**Expected:** `hello-world` prints its banner. If it does not, the Docker daemon is not
running or your user cannot reach the socket. On Windows, confirm Docker Desktop is started
and WSL integration is on. On Linux, confirm you are in the `docker` group.

---

## Step 2 — Start the backing services

The database and object store are defined in `backend/docker-compose.yml` and are the only
services you start with Compose. The application containers are launched by scripts.

```bash
cd backend
docker compose up -d
cd ..
```

This starts three containers:

| Container | Image | Purpose | Ports |
|---|---|---|---|
| `tahirs-list-postgis` | `postgis/postgis:17-3.4` | PostgreSQL 17 + PostGIS 3.4; Flyway migrates on app boot | `5432` |
| `tahirs-list-minio` | `pgsty/silo:latest` | S3-compatible object store for restaurant photos | `9000` (API), `9001` (console) |
| `tahirs-list-minio-init` | `pgsty/silo:latest` | One-shot: waits for the object store, creates the `halal` bucket, exits | none |

> **Why not `minio/minio`?** MinIO deleted `minio/minio` and `minio/mc` from Docker Hub in
> September 2026, and `quay.io/minio` now rejects anonymous pulls, so the previously pinned
> tags no longer resolve on a fresh machine. `pgsty/silo` is a maintained, drop-in
> MinIO-compatible fork; one image serves both the server and the `mc` client used by the
> bucket initializer.

The compose file sets the project name to `tahirs-list`, so the network is always
`tahirs-list_default` and the volumes are prefixed `tahirs-list_`.

### Verify the services are healthy

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose -f backend/docker-compose.yml logs minio-init
```

**Expected:**

- `tahirs-list-postgis` — `Up ... (healthy)`
- `tahirs-list-minio` — `Up ... (healthy)`
- `tahirs-list-minio-init` — `Exited (0)` and its log ends with `MinIO bucket halal ready`

If `minio-init` failed, the bucket does not exist and every image upload will fail later.
Re-run it:

```bash
docker compose -f backend/docker-compose.yml up minio-init --force-recreate
```

---

## Step 3 — Build and run the backend

The backend runs from source inside an `eclipse-temurin:21-jdk` container, with the repo's
`backend/` directory bind-mounted to `/workspace`. The script also ensures the backing
services are up, so it is safe to run even if you skipped Step 2.

```bash
./scripts/backend-live.sh
```

What this does, in order:

1. `docker compose up -d` on the backing services.
2. Removes any existing `tahirs-list-backend` container.
3. Starts a new one with the PostGIS JDBC URL and the
   `APP_S3_*` variables pointing at `http://minio:9000`.
4. Runs `./gradlew --no-daemon :bootstrap:run` inside the container.

**The first run downloads the Gradle wrapper and all dependencies. Budget 5–15 minutes**
depending on network. Subsequent runs reuse the `tahirs-list-gradle-cache` volume and are much
faster. The command returns immediately (the container is detached); the build continues
in the background.

### Watch the build and wait for health

```bash
docker logs -f tahirs-list-backend
```

Look for Ktor's `Application started` / `Responding at http://0.0.0.0:8080` line.
In a second terminal, poll:

```bash
curl -s http://localhost:8080/v1/health
```

Repeat until it returns `200`. This can take several minutes on the first boot because
Flyway applies all 20 migrations and the seed-photo ingest runs.

### What "live" profile changes

`application-live.yml` does two things, and both matter:

- **Real S3 storage.** Without the `live` profile the app uses an in-memory image stub and
  every `GET /v1/listings/{id}/image` returns 404. With it, images go to MinIO.
- **Seed-photo ingest on boot.** A runner reads `seed-photos.json` and stores full +
  thumbnail variants for seeded listings. **It fetches the photo URLs from the internet.**
  Some of those URLs returned 403 during development; the runner logs the failure and
  continues. A handful of seed photos may therefore be missing. That is a known defect, not
  a setup error.

### Config you may need to override

The defaults assume the compose service names and the development credentials. Override by
exporting variables before running the script:

| Variable | Default | When to change |
|---|---|---|
| `APP_S3_ENDPOINT` | `http://minio:9000` | You point at real S3/R2 instead of MinIO |
| `APP_S3_ACCESS_KEY` | `minioadmin` | You change the MinIO credentials |
| `APP_S3_SECRET_KEY` | `minioadmin` | Same |
| `APP_S3_REGION` | `us-east-1` | Your object store requires a real region |
| `APP_S3_BUCKET` | `halal` | You use a different bucket name |

Database credentials are hardcoded in `scripts/backend-live.sh` (`halal`/`halal` against
`postgis:5432`). For a non-local environment, edit the script or set
`APP_DB_*` — but do not commit real credentials.

### About the JWT signing key

No key is configured by default. The app generates an **ephemeral RSA key pair on every
boot**, which means **every backend restart invalidates all issued access tokens** and users
must log in again. That is acceptable for local development and unacceptable in production.

For production, generate a fresh key and supply it base64-encoded:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt.pem
openssl pkcs8 -topk8 -nocrypt -in /tmp/jwt.pem -outform DER | base64 -w0 > /tmp/jwt.b64
```

Pass the contents of `/tmp/jwt.b64` as `app.jwt.rsa-private-key-base64` (env var
`APP_JWT_RSA_PRIVATE_KEY_BASE64`). The loader **refuses to boot** under a `prod` profile if
the key is blank or matches the committed test fixture. Do not use the test key.

---

## Step 4 — Build and run the frontend

Run this **from the repository root**, not from `frontend/`, because the mount path is
relative to the current directory.

```bash
docker rm -f tahirs-list-frontend 2>/dev/null

docker run -d \
  --name tahirs-list-frontend \
  --restart unless-stopped \
  -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app \
  -w /app \
  -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache \
  -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"
```

Notes:

- `API_PROXY_TARGET` **must** be the container name, not `localhost`. Inside the frontend
  container, `localhost` is itself.
- The container performs `npm install`, `next build`, then `next start`. The first build
  takes several minutes. Watch it:
  ```bash
  docker logs -f tahirs-list-frontend
  ```
- The bind mount means source edits are visible immediately, but Next.js production mode
  does **not** hot reload. Rebuild and restart the container to see changes.
- `/app/node_modules` is an anonymous volume so a host `node_modules` cannot poison the
  container build. It is discarded when the container is removed.

### For active frontend development

Production mode is slow to iterate on. Run the dev server in a container instead:

```bash
docker rm -f tahirs-list-frontend-dev 2>/dev/null

docker run -it --rm \
  --name tahirs-list-frontend-dev \
  -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app \
  -w /app \
  -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache \
  -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run dev"
```

---

## Step 5 — Verify end to end

Open `http://localhost:3000`. Then walk this checklist. It exercises every layer, so a
failure tells you which layer broke.

| # | Check | Expected | Layer tested |
|---|---|---|---|
| 1 | `http://localhost:3000` | Home page renders, no error overlay | Next.js |
| 2 | `curl -s localhost:8080/v1/health` | `200`, health JSON | Backend + datasource |
| 3 | `curl -s localhost:8080/v1/api-docs \| head -c 200` | OpenAPI 3.1 JSON begins | Backend routes |
| 4 | Search or browse listings on the home page | Listings appear | Backend → PostGIS → frontend proxy |
| 5 | Open a listing with a photo | Image renders | MinIO → S3 adapter → `/v1/.../image` |
| 6 | Sign up with a fresh email, then log in | Account created, session established | Auth, JWT, Flyway tables |
| 7 | Reload the page while logged in | Session persists | Refresh-token cookie |
| 8 | Restart the backend container and reload | You are **logged out** | Ephemeral JWT key (expected) |

Check 8 is not a bug in your setup — it is the ephemeral-key behavior described in Step 3.
It is the clearest proof that you understand the auth configuration.

### Verify the database and bucket directly (optional)

```bash
docker exec tahirs-list-postgis psql -U halal -d halal -c '\dt'
docker exec tahirs-list-postgis psql -U halal -d halal -c 'select count(*) from users;'
docker exec tahirs-list-minio mc ls local/halal
```

The first should list tables including `users`, `refresh_tokens`, `listings`, and the
verification tables. The third should list stored image objects if seed ingest succeeded.

---

## Step 6 — Run the test suites

Both scripts are self-contained and require only Docker. Run them before you trust the
checkout.

```bash
./scripts/backend-test.sh          # full build + all tests (slow, first run very slow)
./scripts/backend-test.sh test-only  # :persistence:test only (faster smoke check)
./scripts/frontend-test.sh         # npm ci + next build
```

The backend suite uses **Testcontainers**, so it starts real PostGIS containers as siblings.
`scripts/backend-test.sh` handles the two networking requirements for you:

- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` plus
  `--add-host host.docker.internal:host-gateway`, so the build container can reach sibling
  containers bound on the host.
- `TESTCONTAINERS_RYUK_DISABLED=true`, because Ryuk's reaper has the same cross-container
  networking problem. Cleanup falls to `--rm`.

It also mounts `/var/run/docker.sock`. That mount is **root-equivalent on the host** and is
accepted deliberately so tests can manage sibling containers. Do not extend the pattern to
untrusted code.

The test suite uses its **own** Gradle cache volume (`tahirs-list-gradle-test-cache`), separate
from the live backend's (`tahirs-list-gradle-cache`). This is deliberate: Gradle takes a lock on
its user home, so two builds sharing one volume cannot run at the same time. With separate
volumes you can run tests while `tahirs-list-backend` is up. The first test run downloads the Gradle
distribution and dependencies into the new volume and takes a few minutes; later runs are
fast. If you see `Timeout waiting to lock journal cache`, something is sharing a Gradle user
home — see Troubleshooting.

**Expected:** both scripts exit `0`. If the backend suite cannot reach Docker, see
Troubleshooting.

---

## Step 7 — Optional: expose a public preview URL

The original setup used a **named** Cloudflare tunnel so the hostname stayed stable across
restarts. Do not use an ephemeral quick tunnel; it rotates the URL on every restart and
breaks the webhook configuration.

```bash
# One-time, on the machine:
cloudflared tunnel login
cloudflared tunnel create tahirs-list-dev
cloudflared tunnel route dns tahirs-list-dev dev.tahirslist.com
```

Run the tunnel as a container on the same network:

```bash
docker rm -f tahirs-list-tunnel-dev 2>/dev/null

docker run -d \
  --name tahirs-list-tunnel-dev \
  --restart unless-stopped \
  --network tahirs-list_default \
  cloudflare/cloudflared:latest \
  tunnel --no-autoupdate run --token "$CLOUDFLARE_TUNNEL_TOKEN"
```

The tunnel's ingress rule must point `dev.tahirslist.com` at `http://tahirs-list-frontend:3000`
(service name, not `localhost:3000` — the original configuration pointed at
`http://localhost:3000`, which only works when the tunnel shares the host's network
namespace).

The token is a secret. Keep it out of git; `.gitignore` already excludes
`.cloudflared-token.txt` and `.env`.

---

## Step 8 — Optional: automate redeploys without Hermes

**This is a portability trap worth understanding.** The original redeploy automation lived
in `~/.hermes/profiles/adnan/scripts/` — **outside the repository**. It does not travel with
the code. A fresh clone has no webhook receiver, no worker, and no watchdog.

Three honest options:

1. **Manual redeploy (simplest).** After merging to `main`, run:
   ```bash
   git checkout main && git pull
   ./scripts/backend-live.sh
   docker rm -f tahirs-list-frontend
   # re-run the frontend docker run command from Step 4
   ```
2. **GitHub Actions (recommended for a team).** There is **no CI configured** (no
   `.github/workflows/`). Adding a workflow that runs the two test scripts and, on `main`,
   triggers a deploy is the cleanest replacement. It also closes the "no CI" risk in
   [07](07-current-state-and-risks.md).
3. **Port the scripts into the repo.** Move the webhook/worker/watchdog scripts into
   `scripts/` so they are versioned and reviewable. If you do, fix the watchdog first: it
   reads Docker's `.State.Running`, which stays `true` during a crash loop, so it reports a
   crash-looping container as healthy. Check `.State.Restarting`, `.State.ExitCode`, or an
   HTTP health endpoint instead.

---

## Everyday commands

| Task | Command |
|---|---|
| Start backing services | `docker compose -f backend/docker-compose.yml up -d` |
| Stop backing services (keep data) | `docker compose -f backend/docker-compose.yml down` |
| Start/restart backend | `./scripts/backend-live.sh` |
| Rebuild frontend | `docker rm -f tahirs-list-frontend` then re-run Step 4 |
| Backend logs | `docker logs -f tahirs-list-backend` |
| Frontend logs | `docker logs -f tahirs-list-frontend` |
| Health check | `curl -s localhost:8080/v1/health` |
| Backend tests | `./scripts/backend-test.sh` |
| Frontend tests | `./scripts/frontend-test.sh` |
| List containers | `docker ps -a --format 'table {{.Names}}\t{{.Status}}'` |
| MinIO console | `http://localhost:9001` (`minioadmin` / `minioadmin`) |
| Swagger UI | `http://localhost:8080/v1/swagger-ui/index.html` |
| OpenAPI spec | `http://localhost:8080/v1/api-docs` |

## Teardown and reset

```bash
# Stop and remove containers + the network. DATA IS PRESERVED in named volumes.
docker compose -f backend/docker-compose.yml down
docker rm -f tahirs-list-backend tahirs-list-frontend tahirs-list-tunnel-dev 2>/dev/null

# DESTRUCTIVE: also delete the database and object-store data.
docker compose -f backend/docker-compose.yml down -v
docker volume rm tahirs-list-gradle-cache tahirs-list-npm-cache 2>/dev/null

# See what is using disk
docker system df
```

`down -v` deletes `tahirs-list_pgdata` and `tahirs-list_miniodata`. There is no
backup configured. If the data matters, dump it first:

```bash
docker exec tahirs-list-postgis pg_dump -U halal -d halal > ~/halal-backup-$(date +%F).sql
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker not running, or WSL integration off | Start Docker Desktop; enable WSL integration; on Linux check `groups` |
| `permission denied ... /var/run/docker.sock` | User not in `docker` group | `sudo usermod -aG docker $USER`, then re-login |
| `bad interpreter: /bin/sh^M` running `gradlew` | CRLF line endings from a Windows clone | `git config --global core.autocrlf false`, re-clone or `git reset --hard` |
| Backend container exits `127` | Mount path wrong / `gradlew` not executable | Confirm you ran the script from the repo root; `chmod +x backend/gradlew`; check `docker logs tahirs-list-backend` |
| Backend container exits with Flyway error | PostGIS not ready yet | Wait for `(healthy)`, then `docker rm -f tahirs-list-backend && ./scripts/backend-live.sh` |
| `Connection to 172.17.0.1 refused` in tests | Testcontainers networking | Use `scripts/backend-test.sh`, which sets the host override and gateway alias. Do not call Gradle directly. |
| `Timeout waiting to lock journal cache (/gradle-home/...)` | Two Gradle builds share one user home — commonly the live backend and the test suite on the same cache volume | Use `scripts/backend-test.sh` (separate `tahirs-list-gradle-test-cache` volume), or stop `tahirs-list-backend` before testing. |
| Backend tests cannot find Docker socket | Running on macOS/plain container | The script expects `/var/run/docker.sock`. On Docker Desktop for Mac, symlink or run tests inside a Linux VM/WSL2. |
| Frontend container loops with `ENOENT ... /app/package.json` | Wrong mount path, or started outside the repo root | Run the `docker run` from the repository root; verify `docker inspect tahirs-list-frontend --format '{{json .Mounts}}'` |
| Frontend up but API calls 502 | `API_PROXY_TARGET` pointing at `localhost` | Recreate with `http://tahirs-list-backend:8080` |
| Frontend up but 500 from `/v1/*` | Backend still booting | Poll `localhost:8080/v1/health` first |
| Listing images 404 | Backend started without the `live` profile, or bucket missing | Use `./scripts/backend-live.sh`; check `docker logs tahirs-list-minio-init` for `MinIO bucket halal ready` |
| First build seems hung | Gradle downloading dependencies | `docker logs -f tahirs-list-backend`; allow 15 min; check network/proxy |
| `port is already allocated` | Another process on 3000/8080/5432/9000 | `docker ps`, or stop the conflicting service; `lsof -i :3000` |
| Out of disk | Old images, worktrees, caches | `docker system prune`, `docker system df`, remove stale `.worktrees/` |
| Seed photos missing from listings | Upstream URLs return 403 | Known defect. Runner logs and continues. See [07](07-current-state-and-risks.md). |

## What is NOT portable from the old machine

Be explicit with yourself about this list. None of it is in the repository:

| Thing | Where it lived | Consequence |
|---|---|---|
| Redeploy webhook + worker + watchdog | `~/.hermes/profiles/adnan/scripts/` | No automatic deploy on a fresh machine. Use Step 8. |
| Cron schedules (4 jobs) | `~/.hermes/profiles/adnan/cron/jobs.json` | No sprint/coordination automation. Decide whether you want it. |
| Kanban execution history | `~/.hermes/kanban/boards/halal-food-finder/kanban.db` | The work ledger's raw data stays behind. The summary is in [06](06-work-ledger.md). |
| Cloudflare tunnel token | `.cloudflared-token.txt` (gitignored) | You cannot recreate the tunnel without the founder. |
| Agent personas (7 profiles) | `~/.hermes/profiles/*/SOUL.md` | Summarized in [04](04-team-and-roles.md). |
| 42 historical worktrees | `.worktrees/` (gitignored) | Unmerged branches may exist only there. Audit before deleting. |
| Database contents | Docker volume `tahirs-list_pgdata` | A fresh machine starts with an empty database and only seed data. |

If you need the actual data (not just the schema), dump it from the old machine **before**
decommissioning it. This is the single most time-sensitive item in this document.

## Before real users

This setup is a **development** stack. It is not production-safe as written. Before exposing
it to real users:

1. Change the database credentials (`halal`/`halal`) and the MinIO credentials
   (`minioadmin`/`minioadmin`).
2. Supply a real, non-committed JWT signing key and run with the `prod` profile.
3. Add TLS termination in front of the stack (the tunnel provides this; a raw port does not).
4. Configure and **test** database backups and object-store backups. Restore once to prove it.
5. Add CI so tests run on every PR.
6. Move secrets out of scripts and into a secret store or environment injection.
7. Pin image tags instead of `latest` for the tunnel and any added service.

---
