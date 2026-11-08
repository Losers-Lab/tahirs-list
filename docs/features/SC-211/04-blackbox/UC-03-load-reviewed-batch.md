# SC-211 — Phase 4: Black-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** actor-visible behaviour of the catalogue load · **Derived from:** 02 UC-03 · **Delta:** new
> **Security:** the only catalogue write; Tier-1 verified on certifier authority; manual rows unverified; reviewed before merge.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  DC->>TL: 1. Generate update from approved candidates
  TL-->>DC: 2. Migration + diff (brands, verification, hand-cut per declaration, upgrades, claims)
  DC->>TL: 3. Review and merge
  TL->>DB: 4. Apply V22/V23 in one transaction
  DB-->>TL: applied counts
  TL-->>DC: 5. Counts: inserted / upgraded / skipped
  DC->>TL: 6. Verify search, Verified display, hand-cut filter
  TL->>DB: SELECT listings for the metro + hand-cut
  DB-->>TL: rows
  TL-->>DC: 7. Tier-1 verified — hand-cut per declaration — manual unverified
```

**A1 — rejected rows excluded** · survivors only.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: emit survivors — header references the report
```

**A2 — re-apply** · idempotent no-op.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: zero new rows — idempotency proven
```

**A3 — matched existing row** · upgrade, never duplicate.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: UPDATE to VERIFIED — hand-cut per the claim's declaration — attach claim
  DB-->>TL: upgraded, no duplicate
```

**E1 — constraint violation** · rollback, no partial load.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: COMMIT
  DB-->>TL: constraint violation → ROLLBACK
```

**E2 — schema missing** · fail fast.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: check V22
  DB-->>TL: missing → startup fails
```

**E3 — missing/wrong trust state** · blocking.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: verify verification + hand-cut state
  DB-->>TL: wrong state → investigate
```
