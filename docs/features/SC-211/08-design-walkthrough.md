# Design walkthrough — SC-211 Sample ingestion pipeline for halal directory sources

- **Story:** https://app.shortcut.com/halal-food-finder/story/211
- **Artifacts:** `docs/features/SC-211/` (00 → 08; 09–10 follow approval)
- **Walkthrough date:** 2026-11-07
- **Design commit:** this branch/PR (recorded at merge)
- **Status:** awaiting founder decision

### Revision log

| Date | Change | Phases re-run |
|---|---|---|
| 2026-11-07 | Initial walkthrough | — |
| 2026-11-07 | **Founder amendment:** Tier-1 certifier registries are authoritative certificate boards — ingested/upgraded Tier-1 rows are `VERIFIED` + hand-cut, backed by a claim. Manual Zabihah rows stay `UNVERIFIED`. | 0 (ACs amended in Shortcut), 1, 2, 4 (wordings), 5, 6, 7, 8 |

The founder-locked decisions from 2026-11-07 are settled and not re-opened:
automated HMS/HFSAA/HMA; manual cited Zabihah batch; no Crave/Muslim-Foodies
automation; generated Flyway load; all three metros 15–25 rows each;
Photon-only; **Tier-1 registry verification (amended)**. The items below are
what this design needs the founder to ratify.

## 1. Story and domain model

**What it shows:** A new capability — external directory ingestion — producing
*candidates* from public halal directories, and a new entity,
`CertificationClaim`, which records a source's dated, cited assertion about a
venue. **Per the founder amendment, a recognized Tier-1 certifier's registry
entry is an authoritative verification**: the listing becomes `VERIFIED` and
hand-cut with the claim as evidence (`CERTIFIER_REGISTRY` basis). Committee
review remains the route for owner-submitted certificates.

**What it locks in:** (a) claims are the evidence trail backing a verification;
(b) a Tier-1 claim sets `VERIFIED` + hand-cut, and nothing else does; (c) the run
is the audit unit — candidates, rejects, merges, and the report belong to one
run; (d) a matched existing row is upgraded, not duplicated.

**What I need from you:** approve the model (D-3 as amended), and confirm
D-9 (upgrade-on-match) and D-10 (expiry/revocation stays an open flag).

**Known gaps:** Q-1 (HFSAA NY format), Q-2 (Zabihah page availability), Q-3/Q-4
(future surfacing; ODbL/attribution), D-8 (verified-badge attribution copy —
follow-up story).

## 2. Use cases

**What it shows:** Four actor goals: (UC-01) run the Tier-1 sample; (UC-02)
compile the cited Tier-3 manual batch; (UC-03) load a reviewed batch into the
catalogue; (UC-04) audit the run and record evidence. Every acceptance
criterion maps to a step or flow; every exception ends in a defined state (no
silent drops, no partial load). Tier-1 candidates carry `VERIFIED` + hand-cut;
manual-only candidates carry `UNVERIFIED` + `NULL`; matched existing rows are
upgraded.

**What it locks in:** the two-phase shape — **fetch/geocode/report** is separate
from **reviewed load** — which makes each run auditable and keeps the catalogue
write behind code review. A geocoder outage aborts before any candidates
artifact; a source outage does not abort the run. The verification outcome is
visible in `candidates.json` before anything is written to the catalogue.

**What I need from you:** approve the use-case set; ratify the **Data Curator**
actor (D-1); decide the epic home (D-2); confirm the upgrade-on-match rule (D-9).

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
Tier-1 rows display as Verified and pass the hand-cut filter; manual rows do
not. All exceptions end in an externally visible, honest state.

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
guarantee for V23 (brands + listings + matched-row upgrades + claims + search
projection commit or roll back together); the idempotency keys; the verification
outcome rule (Tier-1 claim ⇒ `VERIFIED` + hand-cut; otherwise `UNVERIFIED`); and
the concrete error class behind each black-box exception.

**What it locks in:** the partial unique index and `ON CONFLICT`/`UPDATE`
semantics are the dedupe/upgrade mechanism; failures in the migration fail the
deploy fast (no partial catalogue); audit checks are executable and blocking.

**What I need from you:** approve the transaction/idempotency model; confirm the
widened dedupe index (D-4) is acceptable given the suspected-duplicate report.

**Known gaps:** none beyond D-4.

## 7. Complete class diagram (the design being approved)

**What it shows:** The final module layout — `:domain` (Provenance extension,
`CertificationClaim`, `VerificationBasis`, amended `VerificationStatus`/
`VerificationCommittee` KDoc), `:application` (ingestion use cases/ports), new
`:ingestion` adapter module, `:persistence` (V22 schema + generated V23 data
with verified/unverified outcomes and matched-row upgrades, `Tables.kt`),
`:bootstrap` (`SampleIngestMain` + `ingestSample` task), scripts
(`ingest-sample.sh`, `generate_ingest_migration.py`), the full test inventory
with final names, and the proposed ARCHITECTURE.md addition.

**What it locks in:** module boundaries; migration names and contents; the four
provenance strings; the claims table shape; the registry-verification rule; **no
API and no frontend changes**; no runtime scheduler in this story; the exact
test names that will be written first.

**What I need from you:** approve the complete design, including D-5
(artifacts under `docs/research/ingest-sample-2026-11/`), D-6 (provenance
strings), D-8 (badge attribution follow-up), D-9 (upgrade-on-match) and D-10
(expiry/revocation stays open).

**Known gaps:** none; implementation sequence is Phase 9 (written after
approval).

## Decision items

| # | Question | Recommendation | Your decision |
|---|----------|----------------|---------------|
| D-1 | Ratify a new internal actor **DC — Data Curator** (not an end-user persona) and document it in README/Shortcut? | Yes — internal operations actor; document alongside the six personas | pending |
| D-2 | Story home: keep SC-211 in **M1 Core Search & Discovery** (epic 111) or move to a new "Data Ingestion & Coverage" epic? | Keep in 111 for now; split a dedicated epic only if ingestion becomes an ongoing program of stories | pending |
| D-3 | Model external certification observations as a separate **`CertificationClaim`** entity/table, and treat a recognized Tier-1 claim as the **verification basis** (`CERTIFIER_REGISTRY`) for a `VERIFIED` listing? | Yes — the claim is the evidence trail; committee approval remains the basis for owner-submitted certificates | pending |
| D-4 | Widen the location-dedupe unique index from seed-only to **`WHERE provenance IS NOT NULL`**? | Yes — prevents cross-source duplicate listings; suspected coincident venues are surfaced in the report | pending |
| D-5 | Store the sample artifacts under **`docs/research/ingest-sample-2026-11/`** (snapshots, `candidates.json`, `report.md`, curated batch file)? | Yes — matches the existing seed-research artifact pattern; reviewable in the PR | pending |
| D-6 | Provenance strings: `hms-certified-directory / photon-geocode`, `hfsaa-certified-directory / photon-geocode`, `hma-certified-directory / photon-geocode`, `zabihah-manual-batch / photon-geocode` | Approve — mirrors the existing `research-seed / photon-geocode` convention | pending |
| D-7 | Out of scope confirmation: no runtime scheduler, no claims UI, no automated Zabihah/Crave/Muslim-Foodies, no ODbL display decision | Confirm as stated; follow-ups become their own stories | pending |
| D-8 | Verified-badge attribution: registry-verified rows currently render the same "reviewed by our committee" tooltip as committee-verified rows. Add certifier attribution copy (e.g. "Certified by HMS") as a **follow-up story**, not in SC-211? | Yes — backend-only story; UI attribution is its own frontend task (Fatima/Maryam) | pending |
| D-9 | When a Tier-1 venue matches an existing catalogue row, **upgrade** that row to `VERIFIED` + hand-cut (preserving its provenance) rather than skipping it? | Yes — otherwise the certifier's authoritative status would be silently ignored (AC-6) | pending |
| D-10 | Certification expiry/revocation after registry verification: store expiry on the claim, do **not** auto-demote in this story; revocation handling stays the existing open product flag? | Yes — recorded, not invented; the open flag remains open | pending |

## Approval

> **Design approval requested.** Reply `approved` to start implementation, or name the diagram and the change.

```
Approved by: <pending>
Date:
Design commit:
Scope: phases 1-7 for SC-211
Decision items resolved: D-1 … D-10 (see table above)
```
