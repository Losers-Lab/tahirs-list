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
