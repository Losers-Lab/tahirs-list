# Design walkthrough — SC-211 Sample ingestion pipeline for halal directory sources

- **Story:** https://app.shortcut.com/halal-food-finder/story/211
- **Artifacts:** `docs/features/SC-211/` (00 → 08; 09–10 follow approval)
- **Walkthrough date:** 2026-11-07
- **Design commit:** this branch/PR (recorded at merge)
- **Status:** awaiting founder decision

The founder-locked decisions from 2026-11-07 (recorded in `00-story.md`) are
treated as settled and are not re-opened here: automated HMS/HFSAA/HMA; manual
cited Zabihah batch; no Crave/Muslim-Foodies automation; `is_hand_cut = TRUE`
for Tier-1 with backing claims; generated Flyway load; all three metros 15–25
rows each; UNVERIFIED + Photon-only. The items below are the ones this design
needs the founder to ratify.

## 1. Story and domain model

**What it shows:** A new capability — external directory ingestion — producing
*candidates* from public halal directories, and a new entity,
`CertificationClaim`, which records a source's dated, cited assertion about a
venue. Claims are deliberately separate from our own verification
(`HalalCertification` review): nothing ingested becomes Verified.

**What it locks in:** (a) claims are an evidence trail attached to a listing,
not a trust status; (b) every ingested listing enters at `UNVERIFIED`; (c) the
run is the audit unit — candidates, rejects, merges, and the report belong to
one run; (d) the hand-cut assertion is only ever derived from a Tier-1 claim.

**What I need from you:** approve the model (D-3), and confirm the hand-cut
rule (already locked) is acceptable as modelled in INV-3.

**Known gaps:** Q-1 (HFSAA NY format), Q-2 (Zabihah page availability), Q-3/Q-4
(future surfacing; ODbL/attribution) remain open with owners.

## 2. Use cases

**What it shows:** Four actor goals: (UC-01) run the Tier-1 sample; (UC-02)
compile the cited Tier-3 manual batch; (UC-03) load a reviewed batch into the
catalogue; (UC-04) audit the run and record evidence. Every acceptance
criterion maps to a step or flow; every exception ends in a defined state (no
silent drops, no partial load).

**What it locks in:** the two-phase shape — **fetch/geocode/report** is separate
from **reviewed load** — which makes each run auditable and keeps the catalogue
write behind code review. A geocoder outage aborts before any candidates
artifact; a source outage does not abort the run.

**What I need from you:** approve the use-case set; ratify the **Data Curator**
actor (D-1); decide the epic home (D-2).

**Known gaps:** UC-04's audit is partly human process (spot-checks); the
traceability matrix will carry the evidence.

## 3. Use-case diagram

**What it shows:** The four new use cases inside the "Tahir's List" boundary,
with the Data Curator as the only new actor and five external dependencies
(three directories, Zabihah for the manual batch, Photon). The second diagram
overlays them on the existing agreed system model, marking new items green.

**What it locks in:** no new end-user use case; no change to existing
associations; UC-03 (load) is an operator action, not an end-user feature.

**What I need from you:** approve the diagram; confirm the Data Curator's place
in the persona model (D-1).

**Known gaps:** README/Shortcut persona table update is a follow-up once D-1 is
decided.

## 4. Black-box sequences

**What it shows:** Actor-visible behaviour per use case, flow by flow — the
curator starts a run; the system talks to the directories and Photon; the run
reports counts and rejects; the curator later reviews, merges, and verifies the
catalogue update; the audit reconciles every fetched row.

**What it locks in:** what the operator sees when a source fails (partial run,
explicitly reported), when Photon fails (abort, no candidates), when a row
cannot be matched (rejected with reason), and when the load re-runs (no-op).
All exceptions end in an externally visible, honest state.

**What I need from you:** approve the flow behaviour — especially that a
**partial run is acceptable and reported** (source outage) while a **geocoder
outage aborts** (no unverified-location candidates).

**Known gaps:** the exact report layout is finalised in implementation (the
work-item tests pin its sections).

## 5. System class diagram

**What it shows:** Entity classes from Phase 1; one control class per use case
(`RunSampleIngestion`, `CompileManualBatch`, `LoadIngestionBatch`,
`AuditIngestionRun`); boundaries `IngestionConsole`,
`IngestionReportDocument`, `CatalogUpdateReview`; ports for every external
dependency (`HalalDirectorySource`, `CuratedBatchSource`, `PageFetcher`,
`GeocoderPort`, `ArtifactWriter`, `CatalogBatchWriter`) with named adapter
stubs.

**What it locks in:** every directory sits behind one interface, so adding a
source is an adapter, not a redesign; geocoding stays on the existing
`GeocoderPort`; the catalogue write sits behind `CatalogBatchWriter`, keeping
SQL generation replaceable.

**What I need from you:** approve the shape; confirm no additional source should
be modelled now beyond the locked four (three automated + manual).

**Known gaps:** `Migrations`, `ListingSearchQuery` are existing collaborators
added in a recorded Phase 5 revision.

## 6. White-box sequences

**What it shows:** Exactly how each use case is served by the classes above —
adapter → control → policy → candidate/reject → artifact; the **one-transaction**
guarantee for V23 (brands + listings + claims + search projection commit or
roll back together); the idempotency keys; the error classes behind each
black-box exception.

**What it locks in:** the partial unique index and `ON CONFLICT` semantics are
the dedupe mechanism; failures in the migration fail the deploy fast (no partial
catalogue); audit checks are executable and blocking.

**What I need from you:** approve the transaction/idempotency model; confirm the
widened dedupe index (D-4) is acceptable given the suspected-duplicate report.

**Known gaps:** none beyond D-4.

## 7. Complete class diagram (the design being approved)

**What it shows:** The final module layout — `:domain` (Provenance extension,
`CertificationClaim`), `:application` (ingestion use cases/ports), new
`:ingestion` adapter module, `:persistence` (V22 schema + generated V23 data,
`Tables.kt`), `:bootstrap` (`SampleIngestMain` + `ingestSample` task), scripts
(`ingest-sample.sh`, `generate_ingest_migration.py`), the full test inventory
with final names, and the proposed ARCHITECTURE.md addition.

**What it locks in:** module boundaries; migration names and contents; the four
provenance strings; the claims table shape; **no API and no frontend changes**;
no runtime scheduler in this story; the exact test names that will be written
first.

**What I need from you:** approve the complete design, including D-5
(artifacts under `docs/research/ingest-sample-2026-11/`) and D-6 (provenance
strings).

**Known gaps:** none; implementation sequence is Phase 9 (written after
approval).

## Decision items

| # | Question | Recommendation | Your decision |
|---|----------|----------------|---------------|
| D-1 | Ratify a new internal actor **DC — Data Curator** (not an end-user persona) and document it in README/Shortcut? | Yes — internal operations actor; document alongside the six personas | pending |
| D-2 | Story home: keep SC-211 in **M1 Core Search & Discovery** (epic 111) or move to a new "Data Ingestion & Coverage" epic? | Keep in 111 for now; split a dedicated epic only if ingestion becomes an ongoing program of stories | pending |
| D-3 | Model external certification observations as a separate **`CertificationClaim`** entity/table, not columns on the listing? | Yes — keeps evidence, expiry, and tier independent of trust status | pending |
| D-4 | Widen the location-dedupe unique index from seed-only to **`WHERE provenance IS NOT NULL`**? | Yes — prevents cross-source duplicate listings; suspected coincident venues are surfaced in the report | pending |
| D-5 | Store the sample artifacts under **`docs/research/ingest-sample-2026-11/`** (snapshots, `candidates.json`, `report.md`, curated batch file)? | Yes — matches the existing seed-research artifact pattern; reviewable in the PR | pending |
| D-6 | Provenance strings: `hms-certified-directory / photon-geocode`, `hfsaa-certified-directory / photon-geocode`, `hma-certified-directory / photon-geocode`, `zabihah-manual-batch / photon-geocode` | Approve — mirrors the existing `research-seed / photon-geocode` convention | pending |
| D-7 | Out of scope confirmation: no runtime scheduler, no claims UI, no automated Zabihah/Crave/Muslim-Foodies, no ODbL display decision | Confirm as stated; follow-ups become their own stories | pending |

## Approval

> **Design approval requested.** Reply `approved` to start implementation, or name the diagram and the change.

```
Approved by: <pending>
Date:
Design commit:
Scope: phases 1-7 for SC-211
Decision items resolved: D-1 … D-7 (see table above)
```
