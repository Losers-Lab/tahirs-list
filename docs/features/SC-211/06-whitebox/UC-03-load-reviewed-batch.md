# SC-211 — Phase 6: White-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** how the catalogue load is served, class by class · **Derived from:** 05 + 04/UC-03 · **Delta:** new; one runtime transaction

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant AR as FileRunArtifacts
  participant LOAD as LoadIngestionBatch
  participant VAL as CandidateValidator
  participant BR as BrandRepository
  participant LIST as RestaurantListingRepository
  participant REV as CatalogUpdateReview
  participant SEARCH as ExposedListingSearchQuery
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  DC->>CLI: 1. previewBatch(runId)
  CLI->>AR: 2. readCandidates(runId) [verify candidatesSha256 from run.json]
  AR-->>CLI: candidates.json
  CLI->>LOAD: 3. preview(candidates, runId)
  LOAD->>VAL: 4. validateTrustAndLocation(selection) [claim citation, tier, hand-cut, metro, location]
  VAL-->>LOAD: valid candidates | rejects
  loop each valid candidate
    LOAD->>LIST: 5. findByNormalizedLocation(lat, lng)
    LIST->>DB: SELECT listings at the normalized location
    DB-->>LIST: rows
    LIST-->>LOAD: existing row | none
  end
  LOAD->>BR: 6. resolve(name) [read only — missing brands are planned]
  BR->>DB: SELECT brands by name
  DB-->>BR: brand | none
  BR-->>LOAD: brand | to-create
  LOAD->>REV: 7. build(writes, rejects, reportRef)
  REV-->>LOAD: CatalogUpdateReview (inserts / upgrades / skipped / rejects)
  LOAD-->>CLI: preview
  CLI-->>DC: 8. diffSummary() — no writes
  DC->>CLI: 9. execute(review)
  Note over LIST: 10. Transaction boundary — every write commits or rolls back
  LOAD->>LIST: 11. applyBatch(writes)
  LIST->>DB: 12. INSERT/UPDATE restaurant_listings (verification + hand-cut per declaration)
  LIST->>DB: upsert brands, cuisines, halal items, claims
  LIST->>DB: upsert listing_search where cross_contamination = NO_CROSS_CONTAMINATION
  DB-->>LIST: committed
  LIST-->>LOAD: BatchLoadResult
  LOAD->>AR: 13. markLoaded(runId, loadRunId) [only after commit]
  LOAD-->>CLI: counts
  CLI-->>DC: 14. inserted / upgraded / skipped / rejected
  DC->>SEARCH: 15. search metro + handCut filter + verified display
  SEARCH->>DB: SELECT listings for the metro + hand-cut
  DB-->>SEARCH: rows
  SEARCH-->>DC: 16. Tier-1 verified — manual unverified — claims intact
```

**Idempotency:** the run's `candidatesSha256` is verified before preview and load; brands are matched by name; listings are matched by normalized location (`location_lat_6`, `location_lng_6`); matched rows are updated, never duplicated; claims are unique per (listing, source, record); the search mirror upserts by `id`. The whole batch is one transaction: a failure leaves no partial load and no `loaded.json`.

**Index gate:** Tier-1 certified rows are written with `cross_contamination = NO_CROSS_CONTAMINATION` (D-16), so they reach `listing_search`. Manual/web rows keep their explicit value (default `UNCERTAIN`).

| Phase-4 flow | Error / outcome |
|---|---|
| A1 | Survivors only; header references the report |
| A2 | Re-run matches existing rows/claims → counts zero; an already-loaded run is refused |
| A3 | `UPDATE` matched row to `VERIFIED`; hand-cut per the claim's declaration; attach claim |
| A4 | Validator rejects an unbacked `VERIFIED`/TRUE hand-cut before execute |
| E1 | Any persistence failure → the single transaction rolls back; `loaded.json` is not written |
| E2 | Missing V22 → the load refuses before writing |
| E3 | Wrong verification/hand-cut state for the claim's declaration → blocking (AC-7/INV-1/INV-3) |
| E9 | `candidates.json` changed after the run completed → checksum mismatch, load refuses |

**Authorization:** the load runs with the app's configured DB credentials; the batch file and preview are reviewed by the curator (and PR-reviewed when committed); no new endpoint.
