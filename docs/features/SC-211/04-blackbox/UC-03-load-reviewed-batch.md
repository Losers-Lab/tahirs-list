# SC-211 — Phase 4: Black-box sequence — UC-03 Load a reviewed ingestion batch into the catalog

> **Security / trust flags:** this is the only flow that writes to the catalog.
> All writes are `UNVERIFIED`; trust status cannot be escalated by data. The
> generated migration is reviewed before merge (supply-chain-of-code control).
> Idempotency is a correctness property, not a convenience.

**Actor:** Data Curator.
**External services:** none. The catalog, migration execution, and search are
inside Tahir's List.

## Main success scenario

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List

  DC->>TL: 1. Generate catalog update from approved candidates
  TL-->>DC: 2. Generated migration + diff summary (brands, listings, claims, projection)
  DC->>TL: 3. Review and merge the generated update
  TL->>TL: 4. Apply pending migrations at startup (single transaction)
  TL-->>DC: 5. Applied counts (inserted / merged / skipped)
  DC->>TL: 6. Verify search per metro + hand-cut-only filter on the live stack
  TL-->>DC: 7. Listings visible; provenance and claims intact
```

## Alternate flows

**A1 — the batch has previously rejected rows (from step 2)**

```mermaid
sequenceDiagram
  autonumber
  participant TL as Tahir's List
  TL->>TL: Build the update from the survivors only
  Note over TL: Migration header references the run report for excluded rows
```

**A2 — the migration was already applied (from step 4)**

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: Deploy the same batch again
  TL->>TL: Recognize the applied batch (no-op)
  TL-->>DC: Counts show zero new rows — idempotency confirmed
```

**A3 — a location collides with an existing row (from step 4)**

```mermaid
sequenceDiagram
  autonumber
  participant TL as Tahir's List
  TL->>TL: Insert candidate at an existing normalized location
  TL->>TL: Skip the row (no duplicate listing)
  Note over TL: The report already flags the suspected duplicate for follow-up
```

## Exception flows

**E1 — constraint violation (from step 4)**

```mermaid
sequenceDiagram
  autonumber
  participant TL as Tahir's List
  TL->>TL: Apply migration (provenance CHECK, claim FK, vocabulary)
  TL-->>TL: Violation
  TL->>TL: Roll back the migration transaction
  Note over TL: No partial catalog write; the app does not start on a half-applied batch; curator fixes and regenerates
```

**E2 — schema migration not yet applied (from step 4)**

```mermaid
sequenceDiagram
  autonumber
  participant TL as Tahir's List
  TL->>TL: Apply migrations in order
  Note over TL: Fails fast on the missing V22; merge V22 first, then the data batch
```

**E3 — a listing is not searchable after load (from step 6)**

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: Search the metro
  TL-->>DC: Listing missing from results
  Note over TL: Blocking: investigate projection write vs. cross-contamination gate; not accepted until fixed
```

## Coverage check

Every UC-03 step (1–7) appears as a numbered message; A1–A3 and E1–E3 each have a
fragment.
