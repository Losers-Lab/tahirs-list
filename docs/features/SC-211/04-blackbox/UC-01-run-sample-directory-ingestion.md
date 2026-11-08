# SC-211 — Phase 4: Black-box — UC-01 Run a sample directory ingestion

> **Purpose:** actor-visible behaviour of the Tier-1 sample run · **Derived from:** 02 UC-01 · **Delta:** new
> **Security:** external trust data enters here; no auth surface; no PII; integrity = no invented locations / no silent drops.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor HMS as HMS (external)
  actor HF as HFSAA (external)
  actor HM as HMA (external)
  actor PH as Photon (external)
  participant TL as Tahir's List
  DC->>TL: 1. Run sample (Dallas, NYC, Toronto — caps)
  TL->>HMS: 2. Certified listing — one cached request
  HMS-->>TL: 370 restaurants + snapshot
  TL->>HF: 3. Public locator RPC — one request
  HF-->>TL: 313 entries (New York 26) + snapshot
  TL->>HM: 4. Certified outlets — one REST request
  HM-->>TL: 76 outlets + snapshot
  TL->>TL: 5. Translators map each library model to a source listing
  TL->>TL: 6. Filter to metro restaurants
  TL->>PH: 7. Resolve "name, address, city, region"
  PH-->>TL: Place or no match
  TL->>TL: 8. Reconcile venues, merge claims, set verification + hand-cut per declaration
  TL->>TL: 9. Write snapshots, candidates.json, report.md
  TL-->>DC: 10. Counts per source/metro + rejects
```
**A1 — several entries** · one source listing per entry.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: one listing per directory record — no merging yet (step 5)
```
**A2 — HMS unavailable** · fail that source, keep the others.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `SOURCE_UNAVAILABLE` — cached snapshot if fresh (step 2)
```
**A3 — below cap** · continue, report the shortfall.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: keep confirmed rows only — no padding (step 7)
```
**E1 — source unreachable** · `SOURCE_UNAVAILABLE`, run continues.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: retry/backoff → mark source failed — report what was missed
```
**E2 — Photon down** · abort; no candidates artifact.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: abort before step 9 (`Aborted`)
```
**E3 — source format changed** · quarantine, run continues.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `SOURCE_PARSE_FAILED` — follow-up required
```
**E4 — zero metro matches** · reported; no load.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: zero candidates for the metro (step 6)
```
**E5 — bad geocode** · reject, coordinates never taken.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `WRONG_REGION` / `AMBIGUOUS_GEOCODER_MATCH`
```
**E6 — HFSAA key rotated** · re-discover once from the public bundle.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: 401 → read public locator bundle → retry once
```
**E7 — HMS city blank** · derive from the address, or leave unknown.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: parse city from the address line — never invent one
```
