# SC-211 — Sample ingestion pipeline for halal directory sources

- **Shortcut:** https://app.shortcut.com/halal-food-finder/story/211
- **Epic:** M1 Core Search & Discovery (epic 111) — placement flagged for confirmation (D-2)
- **Iteration:** M1 Sprint 7 — Directory Ingestion & Data Coverage (iteration 210, 2026-11-07 → 2026-11-14)
- **Story type:** feature
- **Priority:** Founder-directed for Sprint 7. Backlog position against the *User
  Story Prioritization* / *Use Case Prioritization* docs was **not independently
  verified** — Adnan to confirm (Q-5).
- **Label:** `ready for design`
- **Workflow:** `@tahirs-feature-workflow` — design-first, founder approval before
  implementation.
- **Requested by:** founder (Arham), 2026-11-07, in-session.

## Personas

| Prefix | Persona | Relevance |
|--------|---------|-----------|
| **DC** *(new — proposed)* | **Data Curator** — internal operations/research actor who runs the pipeline, curates the cited manual batch, reviews generated catalog updates, and audits reports | Primary actor of every use case. **Not yet ratified in README's persona list — decision D-1.** |
| U | User (not logged in / logged in) | Indirect beneficiary: sample listings appear in search. No new user-facing flow. |
| VC | Verification Committee | Not a participant in this feature: ingested listings are UNVERIFIED and external claims do not confer our verification. |
| ET | Extension Trigger | Not affected (browser extensions out of scope). |

## Requirement source

Founder directive plus supply-side research supplied in-session on 2026-11-07.
The research's central finding: North American halal directories split into
**Tier 1 audited certifier registries** (HMS, HFSAA, HMA, AVA/Achahada),
**Tier 2 vetted platforms** (Crave Halal, Muslim Foodies), and
**Tier 3 crowdsourced scale** (Zabihah), and a credible product should *layer*
them rather than treat "halal" as binary.

The founder then locked the decisions below for this sample. Nothing beyond them
is inferred.

### Locked decisions (founder, 2026-11-07)

| # | Decision |
|---|----------|
| 1 | **Automated Tier-1 sources:** HMS (`hmsusa.org/certified-listing`; covers Dallas metro + NYC and publishes certification expiry dates), HFSAA (`hfsaa.org/chapters` → Northeast / New York), HMA (`hmacanada.org/hma-certified-restaurants/`; GTA). |
| 2 | **Tier-3 manual batch:** a small hand-curated, individually cited Zabihah sample (zabihah.com is now an app-shell; **no scraping**). |
| 3 | **Excluded from automation:** Crave Halal (app-only), Muslim Foodies (Google My Maps overlays — Google scraping prohibited by standing ruling), AVA/Achahada (Quebec/Ottawa only — outside the sample metros). |
| 4 | **Hand-cut mapping:** `is_hand_cut = TRUE` for Tier-1 rows; each assertion is backed by a recorded certification claim (certifier, source URL, expiry where published). Manual Zabihah rows keep `NULL`. |
| 5 | **Load path:** committed artifacts → generated, idempotent Flyway migration (the sc-155 seed pattern). No runtime ingestion runner in this story. |
| 6 | **Metros:** Dallas metroplex, NYC, Toronto/GTA — 15–25 listings each. |
| 7 | **Trust posture:** every ingested listing starts `UNVERIFIED`; Photon is the only geocoder; rows that do not pass match-confirmation are rejected with reasons — **no invented coordinates**. |
| 8 | **Sample, not exhaustive** — the pipeline is re-runnable, but this story ingests a capped sample. |

## Acceptance criteria (verbatim, from SC-211)

> **AC-1** A containerized, re-runnable command (`scripts/ingest-sample.sh`)
> fetches the Tier-1 directories for the three metros with polite throttling and
> writes committed artifacts (raw snapshots, `candidates.json`, `report.md`).
>
> **AC-2** Every candidate is geocoded via the existing `GeocoderPort` (Photon)
> with match-confirmation (country/state + name-token); non-matching rows are
> excluded and listed with reasons. No invented coordinates.
>
> **AC-3** The sample yields 15–25 listings per metro, deduplicated within-run by
> tier priority and normalized location; the report maps every listing to its
> source(s).
>
> **AC-4** A generated, idempotent Flyway migration loads the sample as
> UNVERIFIED listings with per-source provenance, plus
> `external_certification_claims` rows citing source URL (+ certifier/expiry
> where published); brands upserted by name.
>
> **AC-5** Tier-1 rows have `is_hand_cut = TRUE`; manual Zabihah rows `NULL`.
> Every hand-cut assertion is backed by a claims row.
>
> **AC-6** Re-applying the migration adds no duplicates, and no existing V7 seed
> row is duplicated (e.g. Paramount Fine Foods).
>
> **AC-7** Sample listings are returned by the existing search API for their
> metros on the live stack, and the hand-cut-only filter includes Tier-1 rows.
>
> **AC-8** The report honestly lists rejects/quarantines with reasons and known
> gaps; design artifacts 00–10 complete.

## Edge cases (recorded, from the founder directive and source recon)

| # | Edge case | Handling expectation |
|---|-----------|----------------------|
| E-1 | HFSAA New York chapter URL/format unverified (recon found `/chicago`, `/detroit`; `/new-york` 404s at time of writing) | Discover chapter links from `/chapters`; if no NY restaurant listings exist, NYC coverage falls back to HMS only — documented in the report |
| E-2 | HMS listing page may be JS-rendered | Fallback: the `shariahboard.org/certified-listings` table (server-rendered; business name, address, phone, state, products, status, expiry) |
| E-3 | Zabihah public listing pages may no longer be fetchable (app-shell) | Cite the founder-supplied research as a secondary source (marked as such) or drop the batch with rationale |
| E-4 | Same venue from two sources (HMS + Zabihah; HMA + an existing V7 seed) | Tier-priority merge within the run; DB dedupe by normalized location; suspected duplicates listed in the report |
| E-5 | Source address lacks city/postal/country | Structured address fields populated where present, `NULL` otherwise — never guessed |
| E-6 | Photon no-match / ambiguous / wrong-country match | Row rejected with a reason; never coordinates from a different venue |
| E-7 | Source rate-limits or is unreachable mid-run | Backoff, then mark that source FAILED; run continues; report states exactly what was and was not fetched |
| E-8 | Fewer than 15 confirmed rows in a metro (e.g. thin coverage) | Report the shortfall honestly; do not pad with unconfirmed rows |

## Open unknowns (owner + when needed)

| # | Unknown | Owner | Needed by |
|---|---------|-------|-----------|
| Q-1 | HFSAA NY chapter listing format and whether it carries full addresses | Aisha / Hamza | WI-03 implementation |
| Q-2 | Whether zabihah.com still serves individual public listing pages | Aisha | WI-05 (manual batch) |
| Q-3 | Whether/when external claims surface in the product UI (badge, expiry display) | Founder / Adnan | Future story — explicitly out of scope |
| Q-4 | ODbL / certifier attribution display policy | Founder | Open flag, `docs/reviews/sc-138-external-services.md` §5 — not decided here |
| Q-5 | Backlog priority against the two prioritization docs | Adnan | Before sprint sign-off |
| Q-6 | Whether the sample batch artifacts live under `docs/research/<dated>/` (recommended) or another home | Founder | Walkthrough (D-5) |
