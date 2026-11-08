# SC-211 — Phase 4: Black-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** actor-visible behaviour of the catalogue load · **Derived from:** 02 UC-03 · **Delta:** new
> **Security:** the only catalogue write; Tier-1 verified on certifier authority; manual rows unverified; reviewed before load; one transaction.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  DC->>TL: 1. Preview the approved candidates
  TL->>DB: 2. Read existing rows + brands
  DB-->>TL: current catalogue
  TL-->>DC: 3. Diff: inserts / upgrades / skipped / rejects (no writes)
  DC->>TL: 4. Execute the reviewed load
  TL->>DB: 5. One transaction: brands, listings, children, claims, search mirror
  DB-->>TL: committed counts
  TL-->>DC: 6. Counts: inserted / upgraded / skipped / rejected
  DC->>TL: 7. Verify search, Verified display, hand-cut filter
  TL->>DB: SELECT listings for the metro + hand-cut
  DB-->>TL: rows
  TL-->>DC: 8. Tier-1 verified — hand-cut per declaration — manual unverified
```

**A1 — rejected rows excluded** · survivors only.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: emit survivors — header references the report
```

**A2 — re-run** · idempotent no-op.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: match by normalized location + claim key
  DB-->>TL: existing rows and claims
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

**A4 — blocking preview error** · no execute offered.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: unbacked VERIFIED or TRUE hand-cut → reject with reason
```

**E1 — constraint/DB failure** · rollback, no partial load.

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
  DB-->>TL: missing → load refuses
```

**E3 — missing/wrong trust state** · blocking.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  participant DB@{ "type": "database" } as PostgreSQL + PostGIS
  TL->>DB: verify verification + hand-cut state
  DB-->>TL: wrong state → investigate
```
