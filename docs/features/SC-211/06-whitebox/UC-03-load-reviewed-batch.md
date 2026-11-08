# SC-211 — Phase 6: White-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** how the catalogue load is served, class by class · **Derived from:** 05 + 04/UC-03 · **Delta:** new; one transaction per migration

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant LOAD as LoadIngestionBatch
  participant WR as SqlMigrationBatchWriter
  participant REV as CatalogUpdateReview
  participant MIG as Migrations
  participant SEARCH as ExposedListingSearchQuery
  DC->>CLI: 1. generateCatalogUpdate(candidates)
  CLI->>LOAD: generate(candidates, runId)
  LOAD->>WR: 2. generate(...) [emits verification + hand-cut per declaration + upgrades + claims]
  WR-->>LOAD: CatalogBatch (SQL + counts)
  LOAD->>REV: build(batch, candidates, reportRef)
  LOAD-->>CLI: CatalogUpdateReview
  CLI-->>DC: 2. diffSummary()
  DC->>CLI: 3. review + merge (PR)
  Note over MIG: 4. Transaction boundary — all batch statements commit or roll back
  MIG->>MIG: apply V22 (schema) → V23 (data)
  MIG-->>REV: appliedCounts()
  CLI-->>DC: 5. inserted / upgraded / skipped
  DC->>SEARCH: 6. search metro + handCut filter + verified display
  SEARCH-->>DC: 7. Tier-1 verified — manual unverified — claims intact
```

**Idempotency:** brands `ON CONFLICT (name)`; listings conflict on normalized location (ingested provenance); matched rows upgraded with `UPDATE … WHERE verification_status = 'UNVERIFIED'` (hand-cut set from the claim's declaration); claims unique per (listing, source, record); projection `ON CONFLICT (id)`.

| Phase-4 flow | Error / outcome |
|---|---|
| A1 | Survivors only; header references the report |
| A2 | Applied version → no-op; counts zero |
| A3 | `UPDATE` matched row to `VERIFIED`; hand-cut per the claim's declaration; attach claim |
| E1 | `ExposedSQLException` → Flyway rolls back; startup fails |
| E2 | Missing V22 → fail fast, data never runs alone |
| E3 | Wrong verification/hand-cut state for the claim's declaration → blocking (AC-7/INV-1/INV-3) |

**Authorization:** migration uses the app's configured DB credentials; merge authorization is PR review of the generated diff; no new endpoint.
