# SC-211 — Sample ingestion pipeline for halal directory sources

> **Purpose:** lock the requirement and its verbatim ACs · **Derived from:** SC-211 + founder directive 2026-11-07 · **Delta:** new story

| Field | Value |
|---|---|
| Shortcut | https://app.shortcut.com/halal-food-finder/story/211 |
| Epic | M1 Core Search & Discovery (111) — placement flagged (D-2) |
| Iteration | M1 Sprint 7 — Directory Ingestion & Data Coverage (210) |
| Type · label | feature · `ready for design` |
| Priority | Founder-directed; backlog position unverified — Q-5 (Adnan) |
| Workflow | `@tahirs-feature-workflow`; approval before implementation |

```mermaid
flowchart LR
  HMS[HMS] --> P["Tier-1 ingestion"]
  HFSAA[HFSAA] --> P
  HMA[HMA] --> P
  ZAB["Zabihah<br/>manual, cited"] --> T3["Tier-3 batch"]
  P --> V["VERIFIED + hand-cut<br/>claim-backed"]
  T3 --> U["UNVERIFIED"]
  V --> CAT[("Catalogue")]
  U --> CAT
```

| Prefix | Persona | Relevance |
|---|---|---|
| **DC** (new, D-1) | Data Curator — internal operator | Runs all four use cases |
| U | User | Sees sample listings in search; no new flow |
| VC | Verification Committee | Not a participant; registry verification is not committee review |
| ET | Extension Trigger | Unaffected |

## Locked decisions (founder, 2026-11-07)

| # | Decision |
|---|---|
| 1 | Automated Tier-1: HMS (Dallas + NYC), HFSAA (NYC), HMA (Toronto/GTA). |
| 2 | Tier-3: hand-curated, individually cited Zabihah sample; no scraping. |
| 3 | Excluded from automation: Crave Halal, Muslim Foodies, AVA/Achada. |
| 4 | Tier-1 rows: `VERIFIED` (the certifier is authoritative for halal), backed by a claim. |
| 5 | Hand-cut is separate: `TRUE` only if the certifier's declared slaughter standard is HAND, `FALSE` if MACHINE, `NULL` if unspecified. Tier is not a hand-cut proxy. |
| 6 | Tier-3 rows: `UNVERIFIED` + `is_hand_cut = NULL`. |
| 7 | Load: committed artifacts → generated, idempotent Flyway migration. |
| 8 | Metros: Dallas, NYC, Toronto; 15–25 rows each. |
| 9 | Photon-only geocoding; non-matching rows rejected — no invented coordinates. |

## Acceptance criteria (verbatim)

> **AC-1** A containerized, re-runnable command (`scripts/ingest-sample.sh`)
> fetches the Tier-1 directories for the three metros with polite throttling and
> writes committed artifacts (raw snapshots, `candidates.json`, `report.md`).

> **AC-2** Every candidate is geocoded via the existing `GeocoderPort` (Photon)
> with match-confirmation (country/state + name-token); non-matching rows are
> excluded and listed with reasons. No invented coordinates.

> **AC-3** The sample yields 15–25 listings per metro, deduplicated within-run by
> tier priority and normalized location; the report maps every listing to its
> source(s).

> **AC-4** A generated, idempotent Flyway migration loads the sample with
> per-source provenance: Tier-1 rows as `VERIFIED`; `is_hand_cut` derived from
> the certifier's declared slaughter standard (HAND → TRUE, MACHINE → FALSE,
> unspecified → NULL); manual Zabihah rows as `UNVERIFIED` + `NULL`; plus
> `external_certification_claims` rows citing source URL (+ certifier/expiry
> where published); brands upserted by name.

> **AC-5** Every `VERIFIED` row is backed by a Tier-1 claim. `is_hand_cut = TRUE`
> requires a claim whose declared slaughter standard is HAND; a Tier-1 certifier
> with a machine or unspecified standard never yields TRUE. Manual Zabihah rows
> are never marked verified or hand-cut.

> **AC-6** Re-applying the migration adds no duplicates, and no existing V7 seed
> row is duplicated (e.g. Paramount Fine Foods); a matched existing row is
> upgraded to `VERIFIED` rather than skipped.

> **AC-7** Sample listings are returned by the existing search API for their
> metros on the live stack; Tier-1 rows display as Verified and are included by
> the hand-cut-only filter; manual Zabihah rows are unverified and excluded from
> that filter.

> **AC-8** The report honestly lists rejects/quarantines with reasons and known
> gaps; design artifacts 00–10 complete.

## Edge cases

| # | Edge case | Handling |
|---|---|---|
| E-1 | HFSAA NY chapter URL/format unverified | Discover links from `/chapters`; fall back to HMS-only NYC if absent |
| E-2 | HMS page JS-rendered | ShariahBoard certified-listings table fallback |
| E-3 | Zabihah pages unavailable | Cite founder research as secondary source, or drop with rationale |
| E-4 | Same venue from two sources | Tier-priority merge; report suspected duplicates |
| E-5 | Partial address (no city/postal) | `NULL` structured fields; never guess |
| E-6 | Photon no-match / wrong region | Reject with reason; no coordinates taken |
| E-7 | Source rate-limits / unreachable | Backoff, mark source failed; run continues; report says what was missed |
| E-8 | Metro below 15 rows | Report the shortfall; never pad |
| E-9 | Certification expires or is revoked | Expiry stored on the claim; revocation stays an open flag (D-10) |
| E-10 | Tier-1 venue matches an existing row | Upgrade that row to `VERIFIED` + hand-cut with claims (AC-6) |
| E-11 | Future Tier-1 certifier permits machine slaughter or is silent on method | `VERIFIED` (halal), `is_hand_cut = FALSE` (machine) or `NULL` (unspecified) — never auto-TRUE |

## Open questions

| # | Unknown | Owner | Needed by |
|---|---|---|---|
| Q-1 | HFSAA NY chapter format and address completeness | Aisha / Hamza | WI-03 |
| Q-2 | Zabihah public listing pages still fetchable | Aisha | WI-05 |
| Q-3 | If/when external claims surface in the UI | Founder / Adnan | Future story |
| Q-4 | ODbL / certifier attribution display policy | Founder | Open flag (sc-138 §5) |
| Q-5 | Backlog priority against the two prioritization docs | Adnan | Sprint sign-off |
| Q-6 | Artifact home: `docs/research/ingest-sample-2026-11/` | Founder | Walkthrough (D-5) |
