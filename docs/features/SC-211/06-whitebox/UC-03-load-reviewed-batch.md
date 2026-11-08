# SC-211 — Phase 6: White-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** how the catalogue load is served, class by class · **Derived from:** 05 + 04/UC-03 · **Delta:** new; one runtime transaction

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant LOAD as LoadIngestionBatch
  participant VAL as CandidateValidator
  participant BR as BrandRepository
  participant LIST as RestaurantListingRepository
  participant REV as CatalogUpdateReview
  participant SEARCH as ExposedListingSearchQuery
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  DC->>CLI: 1. previewBatch(candidates, runId)
  CLI->>LOAD: preview(candidates, runId)
  LOAD->>VAL: 2. validateTrustAndLocation(selection) [claim citation, tier, hand-cut, metro, location]
  VAL-->>LOAD: valid candidates | rejects
  loop each valid candidate
    LOAD->>LIST: 3. findByNormalizedLocation(lat, lng)
    LIST->>DB: SELECT listings at the normalized location
    DB-->>LIST: rows
    LIST-->>LOAD: existing row | none
  end
  LOAD->>BR: 4. resolve(name) [read only — missing brands are planned]
  BR->>DB: SELECT brands by name
  DB-->>BR: brand | none
  BR-->>LOAD: brand | to-create
  LOAD->>REV: 5. build(writes, rejects, reportRef)
  REV-->>LOAD: CatalogUpdateReview (inserts / upgrades / skipped / rejects)
  LOAD-->>CLI: preview
  CLI-->>DC: 6. diffSummary() — no writes
  DC->>CLI: 7. execute(review)
  Note over LIST: 8. Transaction boundary — every write commits or rolls back
  LIST->>DB: 9. INSERT/UPDATE restaurant_listings (verification + hand-cut per declaration)
  LIST->>DB: upsert brands, cuisines, halal items, claims
  LIST->>DB: upsert listing_search where cross_contamination = NO_CROSS_CONTAMINATION
  DB-->>LIST: committed
  LIST-->>LOAD: BatchLoadResult
  LOAD-->>CLI: counts
  CLI-->>DC: 10. inserted / upgraded / skipped / rejected
  DC->>SEARCH: 11. search metro + handCut filter + verified display
  SEARCH->>DB: SELECT listings for the metro + hand-cut
  DB-->>SEARCH: rows
  SEARCH-->>DC: 12. Tier-1 verified — manual unverified — claims intact
```

**Idempotency:** brands matched by name; listings matched by normalized location (`location_lat_6`, `location_lng_6`); matched rows updated, never duplicated; claims unique per (listing, source, record); the search mirror upserts by `id`. The whole batch is one transaction: a failure leaves no partial load.

**Index gate:** Tier-1 certified rows are written with `cross_contamination = NO_CROSS_CONTAMINATION` (D-16), so they reach `listing_search`. Manual/web rows keep their explicit value (default `UNCERTAIN`).

| Phase-4 flow | Error / outcome |
|---|---|
| A1 | Survivors only; header references the report |
| A2 | Re-run matches existing rows/claims → counts zero |
| A3 | `UPDATE` matched row to `VERIFIED`; hand-cut per the claim's declaration; attach claim |
| A4 | Validator rejects an unbacked `VERIFIED`/TRUE hand-cut before execute |
| E1 | Any persistence failure → the single transaction rolls back |
| E2 | Missing V22 → the load refuses before writing |
| E3 | Wrong verification/hand-cut state for the claim's declaration → blocking (AC-7/INV-1/INV-3) |

**Authorization:** the load runs with the app's configured DB credentials; the batch file and preview are reviewed by the curator (and PR-reviewed when committed); no new endpoint.
