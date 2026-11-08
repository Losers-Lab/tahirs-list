# SC-211 — Phase 2: Use cases

Four use cases, one per actor goal. The primary actor for all four is the
proposed **Data Curator (DC)** — an internal operator, not an end-user persona
(see D-1).

---

## UC-01 — Run a sample directory ingestion

- **Primary actor:** Data Curator
- **Supporting actors:** HMS Directory, HFSAA Directory, HMA Directory, Photon Geocoder (external systems)
- **Goal:** produce a geocoded, reconciled candidate batch plus an honest run report from the Tier-1 directories for the three sample metros.
- **Preconditions:** design approved; network access to the Tier-1 directories and Photon; metros and per-metro caps configured; the V22 schema design approved (needed only when the batch is later loaded).
- **Trigger:** the curator invokes the sample-ingestion command with a metro selection.
- **Business rules:**
  - BR-1 Only restaurants are ingested from certifier directories (not processors, distributors, or slaughterhouses).
  - BR-2 A row is kept only if it resolves inside a selected metro's city allowlist.
  - BR-3 Coordinates come only from a confirmed Photon match (country + region + name-token evidence).
  - BR-4 The highest tier wins a venue's provenance; all observing sources' claims are retained.
  - BR-5 Every fetched row is either a candidate or a reject with a reason.
  - BR-6 Fetches are throttled and identify the client; no scraping of prohibited sources.

**Main success scenario**

1. The curator selects metros (Dallas, NYC, Toronto) and per-metro caps (15–25) and starts the run.
2. The system requests the HMS certified-restaurant directory (throttled), falling back to the ShariahBoard certified-listings table if the primary page is unavailable.
3. The system discovers HFSAA chapter links from the chapters page and requests the New York chapter.
4. The system requests the HMA certified-restaurants list for the GTA and the detail pages for the sampled rows.
5. The system filters all fetched rows to restaurants inside the selected metro city allowlists.
6. The system geocodes each remaining row through Photon using "name, address, city, region" and accepts a match only with country + region agreement and name-token overlap.
7. The system reconciles rows describing the same venue (normalized location or normalized name + city) into one candidate, applying tier priority and merging claims.
8. The system writes the raw source snapshots, `candidates.json`, and `report.md` to the run artifact directory.
9. The system reports per-source/per-metro counts and the rejected rows with reasons to the curator.

**Alternate flows**

- **A1 (from step 4, multiple branches)** — A venue appears at several HMA city entries or under several HMS records: each branch is a distinct source listing; reconciliation (step 7) decides whether they are one venue.
- **A2 (from step 2, primary page unavailable)** — The HMS listing page is unavailable or unparseable: the system uses the ShariahBoard certified-listings table as the documented fallback and records which source was used in the report.
- **A3 (from step 6, fewer than the cap)** — Fewer rows than the cap survive filtering: the run continues; the report states the shortfall honestly (no padding).
- **A4 (run repeats within the cache window)** — Snapshots are read from the local cache instead of re-fetching; the report records that cached snapshots were used.

**Exception flows**

- **E1 (from step 2/3/4, source unreachable or rate-limited after retries)** — The source is marked `SOURCE_UNAVAILABLE`; the run continues with the remaining sources; the report names exactly what was not fetched. State: partial run reported, no catalog write.
- **E2 (from step 6, Photon unavailable)** — The run aborts before publishing a candidates artifact (`Aborted` state); the report records the geocoder outage; no partial candidate file is presented as a success.
- **E3 (from step 2/3/4, source format changed / parse fails)** — The source is quarantined as `SOURCE_PARSE_FAILED` with the failing detail; the run continues; a follow-up story is required by UC-04.
- **E4 (from step 5, zero rows match the metro)** — The run completes with zero candidates for that metro and reports it; no load step can proceed for an empty metro.
- **E5 (from step 6, ambiguous or wrong-region geocode)** — The row is rejected (`AMBIGUOUS_GEOCODER_MATCH` / `WRONG_REGION`) and listed with the query and the rejected match for review.

**Postconditions**

- Success: artifacts exist for the run; every fetched row is a candidate or a reject with a reason; no database writes occurred.
- Failure: no candidate artifact is published as a success; the report records the partial/aborted state.

- **Data:** read — external directory pages, geocoder results. Created — artifact files only.
- **Acceptance criteria:** AC-1, AC-2, AC-3, AC-8
- **Open questions:** Q-1 (HFSAA NY format), Q-2 (HMS fallback shape), Q-6 (artifact home).

---

## UC-02 — Compile a cited manual Tier-3 batch

- **Primary actor:** Data Curator
- **Supporting actors:** Zabihah (public pages, external), the founder-supplied research document (secondary source)
- **Goal:** hand-curate a small, individually cited Zabihah sample and merge it into the run's candidate batch under tier-priority rules.
- **Preconditions:** the curator has reviewed the founder-supplied research; a run artifact directory exists (from UC-01) or is created for the batch.
- **Trigger:** the curator decides to include the Tier-3 sample and begins curating entries.
- **Business rules:**
  - BR-7 Manual ingestion is **hand-curated only**; no automated scraping of Zabihah or other apps.
  - BR-8 Every manual row cites a source URL and an access date; a row without a citation is invalid.
  - BR-9 Tier-3 rows never set `is_hand_cut = TRUE`; the observation is recorded as a claim (tier 3).
  - BR-10 Merging with Tier-1 candidates follows UC-01 BR-4 (Tier-1 wins provenance; both claims kept).

**Main success scenario**

1. The curator consults a small set of public Zabihah listing pages and the founder-supplied research.
2. The curator records each venue in the curated batch file: name, address, city, region, country, source URL, access date, optional cuisine/halal flags, tier 3.
3. The system validates the batch file against the manual-entry schema.
4. The system geocodes each valid row through Photon with match-confirmation (as UC-01 step 6).
5. The system reconciles the manual rows with the Tier-1 candidates: a venue already observed by a Tier-1 source is merged (Tier-1 provenance wins; the Tier-3 claim is added); a new venue becomes a separate candidate.
6. The system updates `candidates.json` and `report.md` with the manual rows, their claims, and any merges/rejections.
7. The system reports merged/rejected manual rows with reasons.

**Alternate flows**

- **A1 (from step 2, venue lacks a postal code or has a partial address)** — The row is accepted with `NULL` structured fields; the report notes the gap.
- **A2 (from step 2, the public page is unavailable)** — The curator may cite the founder-supplied research explicitly marked as a secondary source, or omit the venue with a recorded reason (E-3).

**Exception flows**

- **E1 (from step 3, invalid entry: missing citation/URL or malformed fields)** — The entry is rejected as `INVALID_MANUAL_ENTRY` with the field-level reason; the rest of the batch proceeds.
- **E2 (from step 4, no confirmed geocode)** — The row is rejected (`NO_GEOCODER_MATCH`/`AMBIGUOUS_GEOCODER_MATCH`); never forced onto another venue's coordinates.
- **E3 (from step 5, duplicate of a Tier-1 candidate)** — Merged, marked `DUPLICATE_MERGED` in the report, both claims retained; the Tier-1 row remains the listing.

**Postconditions**

- Success: the curated batch is validated, geocoded, merged, and reflected in the run report.
- Failure: invalid rows are rejected with reasons and never enter the batch; the rest of the batch is unaffected.

- **Data:** read — public listing pages/research; created/updated — curated batch file input, `candidates.json`, `report.md`. No database writes.
- **Acceptance criteria:** AC-2, AC-3, AC-5, AC-8
- **Open questions:** Q-2 (page availability), Q-6 (artifact home).

---

## UC-03 — Load a reviewed ingestion batch into the catalog

- **Primary actor:** Data Curator
- **Supporting actors:** none external (the catalog and migration execution are inside the system); the existing search API is used for verification
- **Goal:** turn an approved candidate batch into searchable, unverified catalog listings with provenance and claims — idempotently and transactionally.
- **Preconditions:** design approved; UC-01/UC-02 artifacts reviewed; the V22 schema migration merged; a database available in the target environment.
- **Trigger:** the curator generates the catalog update from the approved candidates and merges it for deployment.
- **Business rules:**
  - BR-11 All listings created by the batch are `UNVERIFIED`.
  - BR-12 The batch is idempotent: re-application changes nothing.
  - BR-13 A listing and its claims are written in one transaction; a failure leaves no partial listing.
  - BR-14 Existing rows (e.g. V7 seeds) are never duplicated; a location collision is skipped and reported.
  - BR-15 Brands are upserted by name before locations.

**Main success scenario**

1. The curator asks the system to generate the catalog update from the approved `candidates.json`.
2. The system generates the Flyway migration (brands, listings, claims, search projection) and a diff summary mapped to the report.
3. The curator reviews the generated migration and diff summary and merges it.
4. The system applies pending migrations at startup in a single transaction.
5. The system reports applied counts (inserted, merged, skipped) to the curator.
6. The curator verifies on the live stack that sample listings are returned by search for each metro and that the hand-cut-only filter includes the Tier-1 rows.
7. The curator records the applied batch and verification evidence in the traceability matrix.

**Alternate flows**

- **A1 (from step 2, some candidates previously rejected)** — Only survivors are emitted; the migration header references the run and its report for the excluded rows.
- **A2 (from step 4, the migration was already applied)** — No-op: counts show zero new rows; idempotency confirmed.
- **A3 (from step 4, a location collides with an existing row)** — The row is skipped (`ON CONFLICT DO NOTHING` semantics); the run report already flags the suspected duplicate for follow-up.

**Exception flows**

- **E1 (from step 4, constraint violation: provenance, claims FK, or vocabulary)** — The migration transaction rolls back; the application does not start on a partially-migrated schema; the curator fixes and regenerates. State: no partial catalog write.
- **E2 (from step 4, schema migration not yet applied)** — Deployment fails fast (migrations run in order); the curator applies/merges V22 first.
- **E3 (from step 6, a listing is not searchable)** — Treated as blocking: investigate projection write vs. cross-contamination gate; no acceptance until fixed.

**Postconditions**

- Success: the catalog holds the new unverified listings and claims; search returns them; idempotency is proven by a re-run; evidence is recorded.
- Failure: no partial load; the failure and its cause are recorded; rollback follows the existing revert-PR procedure.

- **Data:** created — listings, brands (reused), certification claims, search-projection rows, migration file; read — search API results.
- **Acceptance criteria:** AC-4, AC-5, AC-6, AC-7
- **Open questions:** none blocking; D-4 (dedupe-index scope) is a design decision.

---

## UC-04 — Audit an ingestion run and record evidence

- **Primary actor:** Data Curator
- **Supporting actors:** none
- **Goal:** independently confirm the run is honest and complete, record the evidence, and convert gaps into tracked follow-ups.
- **Preconditions:** a run has completed (UC-01/UC-02), and optionally a load has occurred (UC-03).
- **Trigger:** the curator opens the run report for review.
- **Business rules:**
  - BR-16 Every fetched row is accounted for (candidate or reject with a reason) — no silent drops.
  - BR-17 Spot-checks compare a sample of rows against their cited source URLs.
  - BR-18 Any gap, deferral, or suspected defect becomes a Shortcut story, never a code TODO.
  - BR-19 A Tier-1 `is_hand_cut = TRUE` row without a backing claim is a blocking defect.

**Main success scenario**

1. The curator opens the run report.
2. The system presents counts by source × metro, the reject list with reasons, suspected duplicates (including against pre-existing catalogue rows), claims coverage, and known gaps.
3. The curator spot-checks a sample of candidates against their cited source URLs.
4. The curator records the evidence (commands, counts, spot-check results) in the traceability matrix.
5. The curator raises any follow-up (source format change, unavailable source, ambiguous matches, deferred surfacing) as a Shortcut story.

**Alternate flows**

- **A1 (from step 2, a source failed)** — The curator records the failure, confirms the run is still honest about it, and raises a re-run/fix follow-up.
- **A2 (from step 2, shortfall in a metro)** — The shortfall is recorded as a known gap with the reason (coverage thin, matches rejected).

**Exception flows**

- **E1 (from step 2, a fetched row is unaccounted for)** — Blocking defect: the run is not accepted; the accounting bug is fixed before the batch is loaded.
- **E2 (from step 3, a spot-check does not match the cited source)** — Blocking defect: the row is quarantined, the cause investigated (parser vs. geocoder merge), and the run re-executed.
- **E3 (from step 2, a Tier-1 hand-cut row lacks a claim)** — Blocking defect against INV-3; the generator must refuse to emit `TRUE` without a claim.

**Postconditions**

- Success: the report is accepted, evidence is recorded, and every gap is a tracked story.
- Failure: the batch is not loaded until the blocking defect is resolved.

- **Data:** read — run artifacts (`candidates.json`, `report.md`, snapshots); created — traceability entries and Shortcut follow-ups.
- **Acceptance criteria:** AC-8 (and the evidence half of AC-1..AC-7)
- **Open questions:** Q-3, Q-4 remain open and are recorded, not resolved, by this use case.
