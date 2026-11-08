# SC-211 — Phase 4: Black-box — UC-03 Load a reviewed ingestion batch

> **Purpose:** actor-visible behaviour of the catalogue load · **Derived from:** 02 UC-03 · **Delta:** new
> **Security:** the only catalogue write; Tier-1 verified on certifier authority; manual rows unverified; reviewed before merge.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: 1. Generate update from approved candidates
  TL-->>DC: 2. Migration + diff (brands, verified/unverified, upgrades, claims, projection)
  DC->>TL: 3. Review and merge
  TL->>TL: 4. Apply migrations at startup (one transaction)
  TL-->>DC: 5. Counts: inserted / upgraded / skipped
  DC->>TL: 6. Verify search, Verified display, hand-cut filter
  TL-->>DC: 7. Tier-1 verified; manual unverified; claims intact
```

**A1 — rejected rows excluded** · survivors only.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: emit survivors; header references the report
```

**A2 — re-apply** · idempotent no-op.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: zero new rows; idempotency proven
```

**A3 — matched existing row** · upgrade, never duplicate.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `UPDATE` to `VERIFIED` + hand-cut; attach claim
```

**E1 — constraint violation** · rollback, no partial load.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: transaction rolls back; startup fails
```

**E2 — schema missing** · fail fast.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: V22 must be applied first
```

**E3 — missing/wrong trust state** · blocking.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: Tier-1 not Verified / manual Verified → investigate
```
