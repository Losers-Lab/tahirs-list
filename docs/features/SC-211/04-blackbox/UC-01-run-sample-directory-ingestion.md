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
  DC->>TL: 1. Run sample (Dallas, NYC, Toronto — caps, pending policy)
  TL->>TL: 2. Check for an unreviewed run — abort or archive under a unique name
  TL->>PH: 3. Pre-flight test query
  PH-->>TL: place / no match / outage
  TL->>HMS: 4. Certified listing — one cached request
  HMS-->>TL: 370 restaurants + snapshot
  TL->>HF: 5. Public locator RPC — one request
  HF-->>TL: 313 entries (New York 26) + snapshot
  TL->>HM: 6. Certified outlets — one REST request
  HM-->>TL: 76 outlets + snapshot
  TL->>TL: 7. Translators map each library model to a source listing
  TL->>TL: 8. Filter to metro restaurants
  TL->>PH: 9. Resolve "name, address, city, region"
  PH-->>TL: Place or no match
  TL->>TL: 10. Reconcile venues, merge claims, set verification + hand-cut per declaration
  TL->>TL: 11. Write runs/<runId>/: snapshots, candidates.json, report.md, run.json
  TL-->>DC: 12. Counts per source/metro + rejects
```
**A1 — several entries** · one source listing per entry.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: one listing per directory record — no merging yet (step 7)
```
**A2 — HMS unavailable** · fail that source, keep the others.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `SOURCE_UNAVAILABLE` — cached snapshot if fresh (step 4)
```
**A3 — below cap** · continue, report the shortfall.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: keep confirmed rows only — no padding (step 9)
```
**P1 — unreviewed run exists** · abort or archive, never overwrite.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: pending runs listed — archive/<runId>-<timestamp>-<token>/ (step 2)
```
**P2 — Photon down at pre-flight** · abort before any fetch.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: no directories requested — no complete run (step 3)
```
**E1 — source unreachable** · `SOURCE_UNAVAILABLE`, run continues.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: retry/backoff → mark source failed — report what was missed
```
**E2 — Photon down mid-run** · abort; no manifest, so the run is incomplete.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: abort before step 11 (`Aborted`)
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
  TL->>TL: zero candidates for the metro (step 8)
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
