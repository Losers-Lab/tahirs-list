# SC-211 — Phase 4: Black-box — UC-04 Audit an ingestion run

> **Purpose:** actor-visible behaviour of the honest-report audit · **Derived from:** 02 UC-04 · **Delta:** new
> **Security:** amanah control — proves nothing was silently dropped; reads artifacts only.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: 1. Open the run report
  TL-->>DC: 2. Counts, rejects, suspected duplicates, claims/basis coverage, gaps
  DC->>TL: 3. Spot-check rows against cited URLs
  DC->>TL: 4. Record evidence in traceability
  DC->>TL: 5. Raise follow-ups as stories
```

**A1 — source failed** · record + re-run/fix story.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: source marked failed; counts exclude it
```

**A2 — metro shortfall** · known gap with cause.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: below target; reason recorded
```

**E1 — unaccounted row** · blocking.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: row missing from candidates and rejects
```

**E2 — spot-check mismatch** · blocking; quarantine + re-run.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: citation disagrees with the row
```

**E3 — unbacked trust** · blocking.

```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `VERIFIED`/hand-cut without a Tier-1 claim
```
